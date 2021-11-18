package com.example.darby.bolt;

import com.example.darby.dao.H2Dao;
import com.example.darby.document.EstimationScale;
import com.example.darby.document.GameRoom;
import com.example.darby.document.Task;
import com.example.darby.document.TaskEstimation;
import com.example.darby.document.HhUser;
import com.example.darby.dto.CrabTeam;
import com.example.darby.dto.JiraIssuesCreated;
import com.slack.api.app_backend.dialogs.payload.DialogSubmissionPayload;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.context.builtin.DialogSubmissionContext;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.context.builtin.GlobalShortcutContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.request.builtin.DialogSubmissionRequest;
import com.slack.api.bolt.request.builtin.GlobalShortcutRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.dialog.DialogOpenResponse;
import com.slack.api.model.event.MessageChangedEvent;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.model.event.ReactionAddedEvent;
import com.slack.api.model.event.ReactionRemovedEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SlackOnBolt {
  private static final String CREATE_ROOM_SHORTCUT = "create_room_shortcut";
  private static final String ADD_TASK_SHORTCUT = "add_task_shortcut";
  private static final String ROLL_SHORTCUT = "roll_shortcut";
  private static final String ROLL_EVENT = "roll_event";
  private static final String CREATE_ROOM_REQUEST_EVENT = "create_room_request_event";
  private static final String NEXT_TASK_EVENT = "next_task_event";
  private static final String POLL_OPTION_SELECTED_EVENT = "poll_option_selected";
  private static final String POLL_ENDED_EVENT = "poll_ended_event";
  private static final String TASK_ESTIMATED_EVENT = "task_estimated_event";
  private static final String CREATE_JIRA_ISSUE_EVENT = "create_jira_issue_event";

  private final Pattern PORTFOLIO_PATTERN_LONG = Pattern.compile("^https://jira\\.hh\\.ru/browse/(PORTFOLIO-\\d+)$");
  private final Pattern PORTFOLIO_PATTERN_SHORT = Pattern.compile("^(PORTFOLIO-\\d+)$");
  private final Pattern POLL_OPTION_SELECTED_PATTERN = Pattern.compile("^" + POLL_OPTION_SELECTED_EVENT + "-\\w+$");

  private SocketModeApp socketApp;
  private final H2Dao dao;
  private final App slackApp;
  private final WebClient webClient;
  private final String jiraToken;
  private final String xappToken;

  public SlackOnBolt(H2Dao dao,
                     WebClient webClient,
                     App slackApp,
                     @Value("${jira-username}") String jiraUsername,
                     @Value("${jira-password}") String jiraPassword,
                     @Value("${xapp-token}") String xappToken) {
    this.dao = dao;
    this.webClient = webClient;
    this.slackApp = slackApp;

    String jiraCred = jiraUsername + ":" + jiraPassword;
    this.jiraToken = Base64.getEncoder().encodeToString(jiraCred.getBytes());
    this.xappToken = xappToken;
  }

  @Scheduled(initialDelay = 1000, fixedDelay=Long.MAX_VALUE)
  public void init() throws Exception {
    // stub
    slackApp.event(ReactionAddedEvent.class, this::emodzi);
    slackApp.event(ReactionRemovedEvent.class, (payload, ctx) -> ctx.ack());
    slackApp.event(MessageEvent.class, (payload, ctx) -> ctx.ack());
    slackApp.event(MessageChangedEvent.class, (payload, ctx) -> ctx.ack());

    // portfolio flow
    slackApp.globalShortcut(CREATE_ROOM_SHORTCUT, this::handleCreateRoomShortcut);
    slackApp.dialogSubmission(CREATE_ROOM_REQUEST_EVENT, this::handleCreateGameRoom);
    slackApp.blockAction(NEXT_TASK_EVENT, this::handleNextTask);
    slackApp.blockAction(POLL_OPTION_SELECTED_PATTERN, this::handlePollSelect);
    slackApp.blockAction(POLL_ENDED_EVENT, this::handlePollStop);
    slackApp.blockAction(TASK_ESTIMATED_EVENT, this::handleTaskDone);
    slackApp.blockAction(CREATE_JIRA_ISSUE_EVENT, this::handleJiraButton);

    // roll
    slackApp.globalShortcut(ROLL_SHORTCUT, this::handleRollShortcut);
    slackApp.dialogSubmission(ROLL_EVENT, this::handleRollEvent);

    socketApp = new SocketModeApp(xappToken, slackApp);
    socketApp.start();
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

  private Response handleCreateGameRoom(
      DialogSubmissionRequest req,
      DialogSubmissionContext ctx
  ) throws SlackApiException, IOException {
    DialogSubmissionPayload.Channel slackChannel = req.getPayload().getChannel();
    DialogSubmissionPayload.User slackUser = req.getPayload().getUser();
    Map<String, String> formData = req.getPayload().getSubmission();
    String portfolioKey = extractPortfolioKey(formData.get("portfolio_key")).orElseThrow();
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

    ChatPostMessageResponse response = ctx.client().chatPostMessage(r -> r
        .channel(slackChannel.getId())
        .text("Тредик для оценки " + portfolioKey));
    if (!response.isOk()) {
      System.out.println("chatPostMessage failed: " + response.getError());
      return ctx.ack();
    }
    String threadId = response.getTs();

    GameRoom gameRoom = new GameRoom(portfolioKey, estimationScaleId, slackUser.getId(), slackChannel.getId(), threadId);
    Integer gameRoomId = dao.saveGameRoom(gameRoom);
    AtomicInteger idx = new AtomicInteger();
    List<Task> tasks = tasksText.lines().map(title -> new Task(gameRoomId, title, idx.getAndIncrement()))
        .collect(Collectors.toList());
    dao.saveTasks(tasks);

    postNextTaskButton(ctx.client(), gameRoomId, slackChannel.getId(), threadId);

    return ctx.ack();
  }

  private Response handleNextTask(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    String channelId = req.getPayload().getContainer().getChannelId();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();
    String messageId = req.getPayload().getContainer().getMessageTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoom(Integer.valueOf(gameRoomId));
    EstimationScale estimationScale = dao.getEstimationScale(gameRoom.getEstimationScaleId());
    // таска должна быть всегда тк
    // в первый раз таска точно есть,
    // в остальные разы постим кнопку только если есть
    Task currentTask = dao.getCurrentTask(Integer.valueOf(gameRoomId));

    String updBlocks = makePollStartedBody(slackUser.getUsername(), gameRoomId, currentTask.getTitle(),
        estimationScale.getMarks());
    updateSlackMessage(ctx.client(), channelId, messageId, "голосование", updBlocks);

    return ctx.ack();
  }

  private Response handlePollSelect(BlockActionRequest req, ActionContext ctx) throws SlackApiException, IOException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String selected = req.getPayload().getActions().get(0).getValue();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoomByThreadId(threadId);
    EstimationScale estimationScale = dao.getEstimationScale(gameRoom.getEstimationScaleId());
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    dao.updateOrSaveTaskEstimation(currentTask.getId(), slackUser.getUsername(), selected);

    List<String> usersDone = dao.getTaskEstimations(currentTask.getId()).stream()
        .map(TaskEstimation::getSlackUserName).collect(Collectors.toList());

    String updBlocks = malePollUpdatedBody(gameRoom.getId(), currentTask.getTitle(), estimationScale.getMarks(),
        usersDone);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", updBlocks);

    return ctx.ack();
  }

  private Response handlePollStop(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoom(Integer.valueOf(gameRoomId));
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    List<TaskEstimation> taskEstimations = dao.getTaskEstimations(currentTask.getId());

    String blocks = makePollEndedBody(slackUser.getUsername(), taskEstimations);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    return ctx.ack();
  }

  private Response handleTaskDone(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String finalMark = req.getPayload().getActions().get(0).getValue();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoomByThreadId(threadId);
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    currentTask.setFinalMark(finalMark);
    dao.updateTask(currentTask);
    List<TaskEstimation> taskEstimations = dao.getTaskEstimations(currentTask.getId());

    String blocks = makeTaskEndedBody(slackUser.getUsername(), currentTask.getTitle(), taskEstimations, finalMark);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    currentTask = dao.getCurrentTask(gameRoom.getId());
    if (currentTask != null) {
      postNextTaskButton(ctx.client(), gameRoom.getId(), channel.getId(), threadId);
    } else {
      List<Task> tasks = dao.getGameRoomTasks(gameRoom.getId());
      String blocks2 = makeGameRoomEndedBody(gameRoom.getId(), tasks);
      ChatPostMessageResponse response = ctx.client().chatPostMessage(r -> r
          .channel(channel.getId())
          .threadTs(threadId)
          .text("результаты")
          .blocksAsString(blocks2));
      if (!response.isOk()) {
        System.out.println("chatPostMessage failed: " + response.getError());
      }
    }
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

    HhUser user = prepareLdapUser(slackUser.getId());
    createJiraIssues(gameRoom.getPortfolioKey(), tasks, user);

    String portfolioLink = "https://jira.hh.ru/browse/" + gameRoom.getPortfolioKey();
    String blocks = makeJiraDoneBody(tasks, slackUser.getUsername(), portfolioLink);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    return ctx.ack();
  }

  private Optional<String> extractPortfolioKey(String portfolioString) {
    Matcher matcher_long = PORTFOLIO_PATTERN_LONG.matcher(portfolioString);
    if (matcher_long.matches()) {
      return Optional.of(matcher_long.group(1));
    }

    Matcher matcher_short = PORTFOLIO_PATTERN_SHORT.matcher(portfolioString);
    if (matcher_short.matches()) {
      return Optional.of(matcher_short.group(1));
    }

    return Optional.empty();
  }

  private void postNextTaskButton(
      MethodsClient client,
      Integer gameRoomId,
      String channelId,
      String threadId
  ) throws SlackApiException, IOException {
    String body = makeNextTaskButtonBody(gameRoomId);
    ChatPostMessageResponse response = client.chatPostMessage(r -> r
        .channel(channelId)
        .threadTs(threadId)
        .text("начать оценку")
        .blocksAsString(body));
    if (!response.isOk()) {
      System.out.println("chatPostMessage failed: " + response.getError());
    }
  }

  private void updateSlackMessage(
      MethodsClient client,
      String channelId,
      String messageId,
      String title,
      String blocks
  ) throws SlackApiException, IOException {
    var updMessage = client.chatUpdate(r -> r
        .channel(channelId)
        .ts(messageId)
        .text(title)
        .blocksAsString(blocks)
    );
    if (!updMessage.isOk()) {
      System.out.println("chat.postMessage failed: " + updMessage.getError());
    }
  }

  public HhUser prepareLdapUser(String userId) {
    HhUser user = dao.getUserBySlackId(userId);
    if (user.getLdapUserName() == null || user.getLdapTeamName() == null) {
      Pair<CrabTeam, CrabTeam.Mate> crabUser = getCrabUser(user.getSlackUserName());

      user.setLdapUserName(crabUser.getSecond().employee.login);
      if (!crabUser.getFirst().name.startsWith("Команда ")) {
        throw new RuntimeException("Team not found");
      }
      user.setLdapTeamName(crabUser.getFirst().name.substring(8));
      dao.updateUser(user);
    }

    return user;
  }

  public Pair<CrabTeam, CrabTeam.Mate> getCrabUser(String slackUserName) {
    List<CrabTeam> resp;
    try {
      resp = webClient.get()
          .uri(new URI("https://crab.pyn.ru/users"))
          .header("Content-Type", "application/json")
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<List<CrabTeam>>() {})
          .log()
          .block();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("123");
    }

    return resp.stream()
        .map(team -> team.activeMembers.stream().map(mate -> Pair.of(team, mate)))
        .flatMap(Function.identity())
        .filter(item -> ("@" + slackUserName).equals(item.getSecond().employee.slack))
        .findFirst()
        .orElseThrow();
  }

  private void createJiraIssues(String portfolioKey, List<Task> tasks, HhUser user) {
    JiraIssuesCreated creationResponse;
    try {
      String issuesBody = makeJiraIssueBody(tasks, user);
      creationResponse = webClient.post()
          .uri(new URI("https://jira.hh.ru/rest/api/2/issue/bulk"))
          .header("Content-Type", "application/json")
          .header("Authorization", "Basic " + jiraToken)
          .body(BodyInserters.fromValue(issuesBody))
          .retrieve()
          .bodyToMono(JiraIssuesCreated.class)
          .log()
          .block();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("");
    }

    creationResponse.issues.forEach(issue -> {
      try {
        String issueLinkBody = makeJiraIssueLinkBody(issue.key, portfolioKey);
        webClient.post()
            .uri(new URI("https://jira.hh.ru/rest/api/2/issueLink"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + jiraToken)
            .body(BodyInserters.fromValue(issueLinkBody))
            .retrieve()
            .bodyToMono(JiraIssuesCreated.class)
            .log()
            .block();
      } catch (URISyntaxException e) {
        e.printStackTrace();
        throw new RuntimeException("123");
      }
    });
  }

// JIRA
//  так можно вытащить жира команды
//  curl -X GET 'https://jira.hh.ru/rest/api/2/issue/createmeta?projectKeys=HH&issuetypeNames=Task&expand=projects.issuetypes.fields' \
//      --header 'Accept: application/json' \
//      --header 'Authorization: Basic c123=' \
//
//
//  так можно поискать людей
//  curl -X GET 'https://jira.hh.ru/rest/api/2/user/search?username=v.pupkin' \
//      --header 'Accept: application/json' \
//      --header 'Authorization: Basic c1231=' \
//


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


  public String makeJiraDoneBody(List<Task> tasks, String userName, String portfolioLink) {
    String roomResults = tasks.stream()
        .map(t -> t.getTitle() + " - " + t.getFinalMark())
        .collect(Collectors.joining("\n"));

    return """
        [
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "Результаты:\\n%s",
              "emoji": true
            }
          },
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "(%s)\\n%s",
              "emoji": true
            }
          }
        ]
        """.formatted(roomResults, userName, portfolioLink);
  }

  public String makeGameRoomEndedBody(Integer gameRoomId, List<Task> tasks) {
    String roomResults = tasks.stream()
        .map(t -> t.getTitle() + " - " + t.getFinalMark())
        .collect(Collectors.joining("\n"));

    return """
        [
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "Результаты:\\n%s",
              "emoji": true
            }
          },
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Запостить в жиру",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(roomResults, gameRoomId, CREATE_JIRA_ISSUE_EVENT);
  }

  public String makeTaskEndedBody(
      String userName,
      String taskTitle,
      List<TaskEstimation> estimations,
      String finalMark
  ) {
    String pollResults = estimations.stream()
        .map(e -> e.getSlackUserName() + " " + e.getMark())
        .collect(Collectors.joining("\n"));

    return """
        [{
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "(%s)\\n%s\\nРезультаты:\\n%s",
            "emoji": true
          }
        },
        {
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "Итоговая оценка: %s",
            "emoji": true
          }
        }]
        """.formatted(userName, taskTitle, pollResults, finalMark);
  }

  public String makePollEndedBody(String userName, List<TaskEstimation> estimations) {
    String pollResults = estimations.stream()
        .map(e -> e.getSlackUserName() + " " + e.getMark())
        .collect(Collectors.joining("\n"));

    return """
        [{
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "%s завершает голосование",
            "emoji": true
          }
        },
        {
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "Результаты:\\n%s",
            "emoji": true
          }
        },
        {
          "dispatch_action": true,
          "type": "input",
          "element": {
            "type": "plain_text_input",
            "action_id": "%s"
          },
          "label": {
            "type": "plain_text",
            "text": "Итоговая оценка",
            "emoji": true
          }
        }]
        """.formatted(userName, pollResults, TASK_ESTIMATED_EVENT);
  }

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
                    "label": "Ссылка на портфель",
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


  public String makeNextTaskButtonBody(Integer gameRoomId) {
    return """
        [
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Следующая задача",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(gameRoomId, NEXT_TASK_EVENT);
  }

  public String makePollStartedBody(String userName, String gameRoomId, String taskTitle, List<String> marks) {
    String taskTitle1 = taskTitle;
    if (userName != null) {
      taskTitle1 = "(" + userName + ")\n" + taskTitle;
    }
    String taskTitleBlock = """
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            }
          },
      """.formatted(taskTitle1);

    String marksBlock = marks.stream()
        .map(mark -> """
          {
            "type": "button",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            },
            "value": "%s",
            "action_id": "%s-%s"
          }
          """.formatted(mark, mark, POLL_OPTION_SELECTED_EVENT, mark)
        )
        .collect(Collectors.joining(", "));

    return """
        [
        """
        + taskTitleBlock +
        """
          {
            "type": "actions",
            "elements": [
        """
        + marksBlock +
        """
            ]
          },
          {
            "type": "divider"
          },
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Закончить голосование",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(gameRoomId, POLL_ENDED_EVENT);
  }

  public String malePollUpdatedBody(Integer gameRoomId, String taskTitle, List<String> marks, List<String> users) {
    String usersDone = """
        {
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "%s",
            "emoji": true
          }
        },
    """.formatted("Нажали:\n" + String.join("\n", users));

    String taskTitleBlock = """
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            }
          },
      """.formatted(taskTitle);

    String marksBlock = marks.stream()
        .map(mark -> """
          {
            "type": "button",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            },
            "value": "%s",
            "action_id": "%s-%s"
          }
          """.formatted(mark, mark, POLL_OPTION_SELECTED_EVENT, mark)
        )
        .collect(Collectors.joining(", "));

    return """
        [
        """
        + taskTitleBlock +
        """
          {
            "type": "actions",
            "elements": [
        """
        + marksBlock +
        """
            ]
          },
          {
            "type": "divider"
          },
        """
        +
        usersDone
        +
        """
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Завершить голосование",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(gameRoomId, POLL_ENDED_EVENT);
  }

  private String makeJiraIssueBody(List<Task> tasks, HhUser user) {
    String issues = tasks.stream().map(task -> """
        {
            "fields": {
                "project": {
                    "key": "HH"
                },
                "summary": "%s",
                "issuetype": {
                    "name": "Task"
                },
                "assignee":{"name":"%s"},
                "customfield_10961": {"value": "%s"},
                "customfield_11212": %s
            }
        }
        """.formatted(task.getTitle(), user.getLdapUserName(), user.getLdapTeamName(), task.getStoryPoints())
    ).collect(Collectors.joining(", "));

    return """
            {
                "issueUpdates": [
                """
        + issues +
        """
                ]
            }
        """;
  }

  private String makeJiraIssueLinkBody(String issueKey, String portfolioKey) {
    return """
            {
                "type": {
                    "name": "Inclusion"
                },
                "inwardIssue": {
                    "key": "%s"
                },
                "outwardIssue": {
                    "key": "%s"
                }
            }
          """.formatted(portfolioKey, issueKey);
  }

  private Response handleRollShortcut(
      GlobalShortcutRequest req,
      GlobalShortcutContext ctx
  ) throws SlackApiException, IOException {
    String modal = """
        {
            "callback_id": "%s",
            "title": "Roll",
            "submit_label": "Go",
            "elements": [
                {
                    "label": "Диапазон",
                    "type": "text",
                    "name": "roll_range",
                    "hint": "через запятую или пробел, число/два числа/список строк"
                }
            ]
        }
        """.formatted(ROLL_EVENT);

    DialogOpenResponse response = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modal));

    if (!response.isOk()) {
      System.out.println("dialogOpen failed: " + response.getError());
    }

    return ctx.ack();
  }

  private Response handleRollEvent(
      DialogSubmissionRequest req,
      DialogSubmissionContext ctx
  ) throws SlackApiException, IOException {
    DialogSubmissionPayload.Channel slackChannel = req.getPayload().getChannel();
    Map<String, String> formData = req.getPayload().getSubmission();
    List<String> rollRange = Arrays.stream(formData.get("roll_range").split("[\\s,]+")).collect(Collectors.toList());

    String resultPrefix = "roll " + rollRange + ": ";
    String result;

    if (rollRange.size() == 0 || (rollRange.size() == 1 && !isInt(rollRange.get(0)))) { // пусто либо одно нечисло
      return ctx.ack();
    } else if (rollRange.size() == 1 && isInt(rollRange.get(0))) { // одно число
      int range_begin = 0;
      int range_end = Integer.parseInt(rollRange.get(0));
      result = String.valueOf(ThreadLocalRandom.current().nextInt(range_begin, range_end + 1));
    } else if (rollRange.size() == 2 && isInt(rollRange.get(0)) && isInt(rollRange.get(1)) ) { // два числа
      int range_begin = Integer.parseInt(rollRange.get(0));
      int range_end = Integer.parseInt(rollRange.get(1));
      result = String.valueOf(ThreadLocalRandom.current().nextInt(range_begin, range_end + 1));
    } else { // много строк
      int idx = ThreadLocalRandom.current().nextInt(0, rollRange.size());
      result = rollRange.get(idx);
    }

    ChatPostMessageResponse response = ctx.client().chatPostMessage(r -> r
        .channel(slackChannel.getId())
        .text(resultPrefix + result));
    if (!response.isOk()) {
      System.out.println("chatPostMessage failed: " + response.getError());
      return ctx.ack();
    }
    return ctx.ack();
  }

  static boolean isInt(String s) {
    try {
      int i = Integer.parseInt(s);
      return true;
    } catch(NumberFormatException er) {
      return false;
    }
  }

}
