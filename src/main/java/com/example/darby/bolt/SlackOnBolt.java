package com.example.darby.bolt;

import com.example.darby.entity.TaskEstimation;
import com.example.darby.resource.PollHandler;
import static com.example.darby.resource.PollHandler.CREATE_JIRA_ISSUE_EVENT;
import static com.example.darby.resource.PollHandler.NEXT_TASK_EVENT;
import static com.example.darby.resource.PollHandler.POLL_ENDED_EVENT;
import static com.example.darby.resource.PollHandler.POLL_OPTION_SELECTED_PATTERN;
import static com.example.darby.resource.PollHandler.TASK_ESTIMATED_EVENT;
import com.example.darby.resource.RollHandler;
import static com.example.darby.resource.RollHandler.ROLL_EVENT;
import static com.example.darby.resource.RollHandler.ROLL_SHORTCUT;
import com.example.darby.service.CrabHelper;
import com.example.darby.service.JiraHelper;
import com.example.darby.service.SlackHelper;
import com.example.darby.dao.H2Dao;
import com.example.darby.entity.EstimationScale;
import com.example.darby.entity.GameRoom;
import com.example.darby.entity.Task;
import com.example.darby.entity.HhUser;
import com.slack.api.app_backend.dialogs.payload.DialogSubmissionPayload;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.app_backend.interactive_components.payload.MessageShortcutPayload;
import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.context.builtin.DialogSubmissionContext;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.context.builtin.GlobalShortcutContext;
import com.slack.api.bolt.context.builtin.MessageShortcutContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.request.builtin.DialogSubmissionRequest;
import com.slack.api.bolt.request.builtin.GlobalShortcutRequest;
import com.slack.api.bolt.request.builtin.MessageShortcutRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.dialog.DialogOpenResponse;
import com.slack.api.model.event.MessageChangedEvent;
import com.slack.api.model.event.MessageDeletedEvent;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.model.event.MessageFileShareEvent;
import com.slack.api.model.event.ReactionAddedEvent;
import com.slack.api.model.event.ReactionRemovedEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlackOnBolt {
  private static final String CREATE_ROOM_SHORTCUT = "create_room_shortcut";
  private static final String CREATE_ROOM_REQUEST_EVENT = "create_room_request_event";
  private static final String ADD_TASK_SHORTCUT = "add_task_shortcut";
  private static final String ADD_TASK_EVENT = "add_task_event";
  public static final Pattern ADD_TASK_EVENT_PATTERN = Pattern.compile("^" + ADD_TASK_EVENT + "-.*$");
  private static final String CHANGE_TASK_SHORTCUT = "change_task_shortcut";
  private static final String CHANGE_TASK_EVENT = "change_task_event";
  public static final Pattern CHANGE_TASK_EVENT_PATTERN = Pattern.compile("^" + CHANGE_TASK_EVENT + "-.*$");
  private static final String DELETE_TASK_SHORTCUT = "delete_task_shortcut";
  private static final String DELETE_TASK_EVENT = "delete_task_event";
  public static final Pattern DELETE_TASK_EVENT_PATTERN = Pattern.compile("^" + DELETE_TASK_EVENT + "-.*$");


  private final String xappToken;
  private final App slackApp;
  private SocketModeApp socketApp;
  private final H2Dao dao;
  private final CrabHelper crabHelper;
  private final JiraHelper jiraHelper;
  private final SlackHelper slackHelper;
  private final PollHandler pollHandler;
  private final RollHandler rollHandler;

  public SlackOnBolt(H2Dao dao,
                     CrabHelper crabHelper,
                     JiraHelper jiraHelper,
                     SlackHelper slackHelper,
                     PollHandler pollHandler,
                     RollHandler rollHandler,
                     App slackApp,
                     @Value("${xapp-token}") String xappToken) throws Exception {
    this.dao = dao;
    this.crabHelper = crabHelper;
    this.jiraHelper = jiraHelper;
    this.slackHelper = slackHelper;
    this.rollHandler = rollHandler;
    this.pollHandler = pollHandler;
    this.slackApp = slackApp;

    this.xappToken = xappToken;
    slackAppInit();
  }

  public void slackAppInit() throws Exception {
    // stub
    slackApp.event(ReactionAddedEvent.class, this::emodzi);
    slackApp.event(ReactionRemovedEvent.class, (payload, ctx) -> ctx.ack());
    slackApp.event(MessageEvent.class, (payload, ctx) -> ctx.ack());
    slackApp.event(MessageChangedEvent.class, (payload, ctx) -> ctx.ack());
    slackApp.event(MessageFileShareEvent.class, (payload, ctx) -> ctx.ack());
    slackApp.event(MessageDeletedEvent.class, (payload, ctx) -> ctx.ack());

    // roll
    slackApp.globalShortcut(ROLL_SHORTCUT, rollHandler::handleRollShortcut);
    slackApp.dialogSubmission(ROLL_EVENT, rollHandler::handleRollEvent);

    // portfolio flow
    slackApp.globalShortcut(CREATE_ROOM_SHORTCUT, this::handleCreateRoomShortcut);
    slackApp.dialogSubmission(CREATE_ROOM_REQUEST_EVENT, this::handleCreateGameRoom);
    slackApp.messageShortcut(ADD_TASK_SHORTCUT, this::handleAddTaskShortcut);
    slackApp.dialogSubmission(ADD_TASK_EVENT_PATTERN, this::handleAddTask);
    slackApp.messageShortcut(CHANGE_TASK_SHORTCUT, this::handleChangeTaskShortcut);
    slackApp.dialogSubmission(CHANGE_TASK_EVENT_PATTERN, this::handleChangeTask);
    slackApp.messageShortcut(DELETE_TASK_SHORTCUT, this::handleDeleteTaskShortcut);
    slackApp.dialogSubmission(DELETE_TASK_EVENT_PATTERN, this::handleDeleteTask);

    slackApp.blockAction(NEXT_TASK_EVENT, pollHandler::handleStartTaskPoll);
    slackApp.blockAction(POLL_OPTION_SELECTED_PATTERN, pollHandler::handlePollSelect);
    slackApp.blockAction(POLL_ENDED_EVENT, pollHandler::handlePollStop);
    slackApp.blockAction(TASK_ESTIMATED_EVENT, pollHandler::handleTaskDone);

    slackApp.blockAction(CREATE_JIRA_ISSUE_EVENT, this::handleJiraButton);

    socketApp = new SocketModeApp(xappToken, slackApp);
    socketApp.startAsync();
  }

  @PreDestroy
  public void shutdown() throws Exception {
    socketApp.stop();
  }

  public Response handleCreateRoomShortcut(
      GlobalShortcutRequest req,
      GlobalShortcutContext ctx
  ) throws SlackApiException, IOException {
    List<EstimationScale> estimationScales = dao.getAllEstimationScales(req.getPayload().getUser().getId());
    String modal = makeRoomCreationModalBody(estimationScales);

    DialogOpenResponse response = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modal));

    if (!response.isOk()) {
      System.out.println("dialogOpen failed: " + response.getError());
    }

    return ctx.ack();
  }

  // dialog rules https://api.slack.com/dialogs
  public String makeRoomCreationModalBody(List<EstimationScale> estimationScales) {
    String scaleOptions = estimationScales.stream()
        .map(item -> """
          {
            "label": "%s: %s",
            "value": "%s"
          },
          """.formatted(item.getName(), item.getMarks(), item.getId())
        )
        .collect(Collectors.joining(" "));
    return """
        {
            "callback_id": "%s",
            "title": "Создать комнату",
            "submit_label": "Создать",
            "elements": [
                {
                    "label": "Портфель или ссылка на портфель",
                    "type": "text",
                    "name": "portfolio_key"
                },
                {
                    "label": "Список задач",
                    "type": "textarea",
                    "name": "tasks_text",
                    "hint": "на отдельных строчках"
                },
                {
                    "label": "Выбрать шкалу",
                    "type": "select",
                    "name": "estimation_scale_id",
                    "options": [
        """.formatted(CREATE_ROOM_REQUEST_EVENT)
        + scaleOptions +
        """
                        {
                          "label": "Новая шкала",
                          "value": "new_scale"
                        }
                      ]
                },
                {
                    "label": "Своя шкала",
                    "type": "text",
                    "name": "new_estimation_scale",
                    "optional": true
                }
            ]
        }
        """;
  }

  private Response handleCreateGameRoom(
      DialogSubmissionRequest req,
      DialogSubmissionContext ctx
  ) throws SlackApiException, IOException {
    DialogSubmissionPayload.Channel slackChannel = req.getPayload().getChannel();
    DialogSubmissionPayload.User slackUser = req.getPayload().getUser();
    Map<String, String> formData = req.getPayload().getSubmission();
    String portfolioKey = jiraHelper.extractPortfolioKey(formData.get("portfolio_key")).orElseThrow();
    String tasksText = formData.get("tasks_text");
    String formEstimationScaleId = formData.get("estimation_scale_id");
    String newEstimationScale = formData.get("new_estimation_scale");

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    Integer estimationScaleId;
    if (formEstimationScaleId.equals("new_scale")) {
      EstimationScale estimationScale = new EstimationScale("Своя шкала", List.of(newEstimationScale.split("[\\s,]+")));
      if (estimationScale.getMarks().size() == 0) {
        return ctx.ack();
      }
      estimationScaleId = dao.saveEstimationScaleId(estimationScale);
    } else {
      estimationScaleId = Integer.valueOf(formEstimationScaleId);
    }

    ChatPostMessageResponse response = slackHelper.postSlackMessage(slackChannel.getId(), null,
        "Тредик для оценки", makeMainMessageBody(portfolioKey));
    String threadId = response.getTs();

    GameRoom gameRoom = new GameRoom(portfolioKey, estimationScaleId,
        slackUser.getId(), slackChannel.getId(), threadId);
    Integer gameRoomId = dao.saveGameRoom(gameRoom);
    AtomicInteger idx = new AtomicInteger();
    List<Task> tasks = tasksText.lines().map(title -> new Task(gameRoomId, title, idx.getAndIncrement()))
        .collect(Collectors.toList());
    dao.saveTasks(tasks);

    slackHelper.postSlackMessage(slackChannel.getId(), threadId,
        "Начать оценку", makeNextTaskButtonBody(gameRoom.getId()));

    return ctx.ack();
  }

  public String makeMainMessageBody(String portfolioKey) {
    String portfolioLink = "https://jira.hh.ru/browse/" + portfolioKey;

    return "[" +
        slackHelper.makeMarkDownBlock("Тредик для оценки <%s|%s>".formatted(portfolioLink, portfolioKey)) +
        "]";
  };

  private String makeNextTaskButtonBody(Integer gameRoomId) {
    return "[" +
        slackHelper.makeButtonBlock("Следующая задача", String.valueOf(gameRoomId), NEXT_TASK_EVENT) +
        "]";
  }

  public Response handleAddTaskShortcut(
      MessageShortcutRequest req,
      MessageShortcutContext ctx
  ) throws SlackApiException, IOException {
    String threadId = req.getPayload().getMessageTs();

    String modal = """
        {
            "callback_id": "%s-%s",
            "title": "Добавить задачи",
            "submit_label": "Добавить",
            "elements": [
                {
                    "label": "Список задач",
                    "type": "textarea",
                    "name": "tasks_text",
                    "hint": "на отдельных строчках"
                }
            ]
        }
        """.formatted(ADD_TASK_EVENT, threadId);

    DialogOpenResponse response = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modal));

    if (!response.isOk()) {
      System.out.println("dialogOpen failed: " + response.getError());
    }

    return ctx.ack();
  }

  private Response handleAddTask(DialogSubmissionRequest req, DialogSubmissionContext ctx) {
    DialogSubmissionPayload.User slackUser = req.getPayload().getUser();
    Map<String, String> formData = req.getPayload().getSubmission();
    String tasksText = formData.get("tasks_text");
    String threadId = req.getPayload().getCallbackId().replace(ADD_TASK_EVENT + "-", "");

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoomByThreadId(threadId);
    List<Task> existsTasks = dao.getGameRoomTasks(gameRoom.getId());
    int lastOrder = existsTasks.get(existsTasks.size() - 1).getTaskOrder();
    AtomicInteger idx = new AtomicInteger(lastOrder + 1);
    List<Task> tasks = tasksText.lines().map(title -> new Task(gameRoom.getId(), title, idx.getAndIncrement()))
        .collect(Collectors.toList());
    dao.saveTasks(tasks);

    return ctx.ack();
  }

  public Response handleChangeTaskShortcut(
      MessageShortcutRequest req,
      MessageShortcutContext ctx
  ) throws SlackApiException, IOException {
    MessageShortcutPayload.User slackUser = req.getPayload().getUser();
    String messageId = req.getPayload().getMessageTs();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));
    Task task = dao.getTaskByMessageId(messageId);

    String modal = """
        {
            "callback_id": "%s-%s",
            "title": "%s",
            "submit_label": "Изменить",
            "elements": [
                {
                  "label": "Новое название",
                  "type": "text",
                  "name": "tasks_text"
                }
                
            ]
        }
        """.formatted(CHANGE_TASK_EVENT, messageId, task.getTitle());

    DialogOpenResponse response = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modal));

    if (!response.isOk()) {
      System.out.println("dialogOpen failed: " + response.getError());
    }

    return ctx.ack();
  }

  private Response handleChangeTask(DialogSubmissionRequest req,
                                    DialogSubmissionContext ctx) throws SlackApiException, IOException {
    DialogSubmissionPayload.User slackUser = req.getPayload().getUser();
    String channelId = req.getPayload().getChannel().getId();
    Map<String, String> formData = req.getPayload().getSubmission();
    String formText = formData.get("tasks_text");
    String messageId = req.getPayload().getCallbackId().replace(CHANGE_TASK_EVENT + "-", "");

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));
    Task task = dao.getTaskByMessageId(messageId);
    task.setTitle(formText);
    dao.updateTaskField(task.getId(), "title", formText);

    GameRoom gameRoom = dao.getGameRoom(task.getGameRoomId());
    EstimationScale estimationScale = dao.getEstimationScale(gameRoom.getEstimationScaleId());
    List<TaskEstimation> taskEstimations = dao.getTaskEstimations(task.getId());

    pollHandler.updateTaskMessage(task, estimationScale.getMarks(), channelId, slackUser.getName(), taskEstimations);

    return ctx.ack();
  }

  public Response handleDeleteTaskShortcut(
      MessageShortcutRequest req,
      MessageShortcutContext ctx
  ) throws SlackApiException, IOException {
    MessageShortcutPayload.User slackUser = req.getPayload().getUser();
    String messageId = req.getPayload().getMessageTs();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));
    dao.getTaskByMessageId(messageId); // чтобы упасть если ткнули на не таске

    String modal = """
        {
            "callback_id": "%s-%s",
            "title": "Точно удалить?",
            "submit_label": "Удалить",
            "elements": [
                {
                  "label": "Почему",
                  "type": "text",
                  "name": "tasks_text",
                  "optional": true
                }
                
            ]
        }
        """.formatted(DELETE_TASK_EVENT, messageId);

    DialogOpenResponse response = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modal));

    if (!response.isOk()) {
      System.out.println("dialogOpen failed: " + response.getError());
    }

    return ctx.ack();
  }

  private Response handleDeleteTask(DialogSubmissionRequest req,
                                    DialogSubmissionContext ctx) throws SlackApiException, IOException {
    DialogSubmissionPayload.User slackUser = req.getPayload().getUser();
    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    String slackChannelId = req.getPayload().getChannel().getId();
    String messageId = req.getPayload().getCallbackId().replace(DELETE_TASK_EVENT + "-", "");

    Task task = dao.getTaskByMessageId(messageId);
    task.setDeleted(true);
    dao.updateTaskField(task.getId(), "deleted", true);

    GameRoom gameRoom = dao.getGameRoom(task.getGameRoomId());

    pollHandler.updateTaskMessage(task, List.of(), slackChannelId, slackUser.getName(), List.of());

    if (!gameRoom.getEnded()) {
      pollHandler.prepareNextTask(gameRoom, slackChannelId, gameRoom.getThreadId());
    }

    return ctx.ack();
  }

  // so stub
  public Response emodzi(EventsApiPayload<ReactionAddedEvent> payload, EventContext ctx) {
//    ReactionAddedEvent event = payload.getEvent();
//    if (event.getReaction().equals("white_check_mark")) {
//      ChatPostEphemeralResponse message = ctx.client().chatPostEphemeral(r -> r
//          .channel(event.getItem().getChannel())
//          .threadTs(event.getItem().getTs())
//          .user(event.getUser())
//          .text("<@" + event.getUser() + "> Thank you! We greatly appreciate your efforts :two_hearts:"));
//      if (!message.isOk()) {
//        System.out.println("chat.postMessage failed: " + message.getError());
//      }
//    }
    return ctx.ack();
  }

  private Response handleJiraButton(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));
    GameRoom gameRoom = dao.getGameRoomByThreadId(threadId);
    List<Task> tasks = dao.getGameRoomTasks(gameRoom.getId());

    HhUser user = crabHelper.prepareLdapUser(slackUser.getId());
    jiraHelper.createJiraIssues(gameRoom.getPortfolioKey(), tasks, user);

    String portfolioLink = "https://jira.hh.ru/browse/" + gameRoom.getPortfolioKey();
    String blocks = makeJiraDoneBody(tasks, slackUser.getUsername(), portfolioLink);
    slackHelper.updateSlackMessage(channel.getId(), messageId, "Голосование", blocks);

    return ctx.ack();
  }

  private String makeJiraDoneBody(List<Task> tasks, String userName, String portfolioLink) {
    String roomResults = tasks.stream()
        .map(t -> t.getTitle() + " - " + t.getFinalMark())
        .collect(Collectors.joining("\n"));

    Optional<Integer> sumStoryPointsOpt = jiraHelper.makeSumStoryPoints(tasks);
    String sumStoryPoints = sumStoryPointsOpt.map(s -> " (" + s + ")").orElse("");

    return "[" +
        slackHelper.makePlainTextBlock("Результаты%s:".formatted(sumStoryPoints)) + "," +
        slackHelper.makePlainTextBlock(roomResults) + "," +
        slackHelper.makeMarkDownBlock("(%s)\n<%s|Задачи заведены>".formatted(userName, portfolioLink)) + "," +
        "]";
  }
}
