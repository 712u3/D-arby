package com.example.darby.deleteme;

import com.example.darby.dao.MongoDao;
import com.example.darby.documents.EstimationScale;
import com.example.darby.documents.GameRoom;
import com.example.darby.documents.Task;
import com.example.darby.documents.TaskEstimation;
import com.slack.api.app_backend.dialogs.payload.DialogSubmissionPayload;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
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
import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.dialog.DialogOpenResponse;
import com.slack.api.model.event.MessageChangedEvent;
import com.slack.api.model.event.ReactionAddedEvent;
import com.slack.api.model.event.ReactionRemovedEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlackOnBolt {
  private static final String CREATE_ROOM_MODAL = "darby_play_id";
  private static final String CREATE_ROOM_REQUEST = "create_room_request";
  private static final String NEXT_TASK = "start_game_id";
  private static final String POLL_OPTION_1 = "actionId-31231";
  private static final String POLL_END = "actionId-1212";
  private static final String POLL_END2 = "plain_text_input-action452524";
  private static final String POLL_END3 = "jirabutton-action452524";

  private SocketModeApp socketApp;
  private final MongoDao mongoDao;

  public SlackOnBolt(MongoDao mongoDao) {
    this.mongoDao = mongoDao;
  }

  @Scheduled(initialDelay = 1000, fixedDelay=Long.MAX_VALUE)
  public void init() throws Exception {
    initDataSourceProxyFactory();
  }

//  @PostConstruct
  public void initDataSourceProxyFactory() throws Exception {
    App app = new App();

    // сообщение в тред за эмодзи
    app.event(ReactionAddedEvent.class, this::emodzi);
    app.event(ReactionRemovedEvent.class, (payload, ctx) -> {
      return ctx.ack();
    });

//    // сообщение с кнопками за сообщение
//    // https://api.slack.com/messaging/interactivity#components
//    app.event(MessageEvent.class, this::apply2);
//    // обработка кликания по форме из ^
//    app.blockAction("zp10", (req, ctx) -> {
//      return ctx.ack();
//    });

    // апдейты сообщений
    app.event(MessageChangedEvent.class, (payload, ctx) -> {
      return ctx.ack();
    });

    // показать модал форму с тригера
    app.globalShortcut(CREATE_ROOM_MODAL, this::getRoomCreationModal);
    app.dialogSubmission(CREATE_ROOM_REQUEST, this::createRoomRequest);
    app.blockAction(NEXT_TASK, this::nextTask);
    app.blockAction(Pattern.compile("^" + POLL_OPTION_1 + "-\\w+" + "$"), this::pollEvent);
    app.blockAction(POLL_END, this::pollEnd);
    app.blockAction(POLL_END2, this::pollEnd2);
    app.blockAction(POLL_END3, this::jiraPost);

    socketApp = new SocketModeApp(app);
    socketApp.start();
  }

  @PreDestroy
  public void shutdown() throws Exception {
    socketApp.stop();
  }


  // просто показываем модалку
  public Response getRoomCreationModal(GlobalShortcutRequest req, GlobalShortcutContext ctx) throws SlackApiException, IOException {
    List<EstimationScale> estimationScales = mongoDao.getAllEstimationScales(req.getPayload().getUser().getId());
    String modalJson = makeCreateRoomModalJson(estimationScales);

    DialogOpenResponse apiResponse = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modalJson));
    if (!apiResponse.isOk()) {
      System.out.println("chat.postMessage failed: " + apiResponse.getError());
      System.out.println(apiResponse.getResponseMetadata());
    }

    return ctx.ack();
  }

  // реквест заполненной модалки
  private Response createRoomRequest(DialogSubmissionRequest req, DialogSubmissionContext ctx) throws SlackApiException, IOException {
    DialogSubmissionPayload.Channel channel = req.getPayload().getChannel();
    DialogSubmissionPayload.User user = req.getPayload().getUser();
    Map<String, String> data = req.getPayload().getSubmission();
    String title = data.get("my_name_1");
    String tasksText = data.get("my_name_2");
    String estimationScaleId = data.get("my_name_3");

    if ("new_scale".equals(estimationScaleId)) {
      EstimationScale estimationScale = new EstimationScale(
          "Своя шкала",
          List.of(data.get("my_name_4").split("[\\s,]+"))
      );
      estimationScaleId = mongoDao.getOrSave(estimationScale);
    }

    // постим сообщение в треде которого будет весь движ
    ChatPostMessageResponse message = ctx.client().chatPostMessage(r -> r
        .channel(channel.getId())
        .text("тредик для оценки " + title));
    if (!message.isOk()) {
      System.out.println("chat.postMessage failed: " + message.getError());
    }
    var threadId = message.getTs();

    List<Task> tasks = tasksText.lines()
        .map(taskTitle -> new Task(taskTitle, List.of()))
        .collect(Collectors.toList());
    GameRoom gameRoom = new GameRoom(title, estimationScaleId, user.getId(), channel.getId(), threadId, tasks);
    String gameRoomId = mongoDao.save(gameRoom).getId();

    // постим в тред первое сообщение - кнопка "следующая задача"
    postNextTaskButton(ctx.client(), gameRoomId, channel.getId(), threadId);

    return ctx.ack();
  }

  // кто-то нажал на кнопку следующая задача
  private Response nextTask(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    var channelId = req.getPayload().getContainer().getChannelId();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();
    var messageId = req.getPayload().getContainer().getMessageTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();

    GameRoom gameRoom = mongoDao.getGameRoom(gameRoomId);
    EstimationScale estimationScale = mongoDao.getEstimationScale(gameRoom.getEstimationScaleId());
    Task nextTask = gameRoom.getNextTask().get();

    var updBlocks = pollJson1(user.getUsername(), gameRoomId, nextTask.getTitle(), estimationScale.getMarks());
    updateMessage(ctx.client(), channelId, messageId, "голосование", updBlocks);

    return ctx.ack();
  }

  private Response pollEvent(BlockActionRequest req, ActionContext ctx) throws SlackApiException, IOException {
    var channel = req.getPayload().getChannel();
    var messageId = req.getPayload().getMessage().getTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();
    var selected = req.getPayload().getActions().get(0).getValue();


    GameRoom gameRoom = mongoDao.getGameRoomByThreadId(threadId);
    EstimationScale estimationScale = mongoDao.getEstimationScale(gameRoom.getEstimationScaleId());
    Task nextTask = gameRoom.getNextTask().get();

    // race condition?
    Optional<TaskEstimation> lastEstimation = nextTask.getEstimations().stream()
        .filter(e -> e.getUserName().equals(user.getUsername()))
        .findFirst();
    if (lastEstimation.isEmpty()) {
      nextTask.getEstimations().add(new TaskEstimation(user.getUsername(), selected));
    } else {
      lastEstimation.get().setMark(selected);
    }
    mongoDao.save(gameRoom);

    List<String> users = nextTask.getEstimations().stream()
        .map(TaskEstimation::getUserName)
        .filter(userName -> !userName.equals(user.getUsername()))
        .collect(Collectors.toList());
    users.add(user.getUsername());

    var updBlocks = pollJson2(gameRoom.getId(), nextTask.getTitle(), estimationScale.getMarks(), users);
    updateMessage(ctx.client(), channel.getId(), messageId, "голосование", updBlocks);

    return ctx.ack();
  }

  private Response pollEnd(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    var channel = req.getPayload().getChannel();
    var messageId = req.getPayload().getMessage().getTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();
    var gameRoomId = req.getPayload().getActions().get(0).getValue();

    GameRoom gameRoom = mongoDao.getGameRoom(gameRoomId);
    Task nextTask = gameRoom.getNextTask().get();

    var blocks = makePollEndJson(user.getUsername(), nextTask.getEstimations());
    updateMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    return ctx.ack();
  }

  private Response pollEnd2(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    var channel = req.getPayload().getChannel();
    var messageId = req.getPayload().getMessage().getTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();
    var finalMark = req.getPayload().getActions().get(0).getValue();

    GameRoom gameRoom = mongoDao.getGameRoomByThreadId(threadId);
    Task nextTask = gameRoom.getNextTask().get();

    nextTask.setFinalMark(finalMark);
    mongoDao.save(gameRoom);

    var blocks = makePollEndJson2(user.getUsername(), nextTask.getTitle(), nextTask.getEstimations(), finalMark);
    updateMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    if (gameRoom.getNextTask().isPresent()) {
      postNextTaskButton(ctx.client(), gameRoom.getId(), channel.getId(), threadId);
    } else {
      var blocks2 = makeRoomEndJson(gameRoom);
      ChatPostMessageResponse message2 = ctx.client().chatPostMessage(r -> r
          .channel(channel.getId())
          .threadTs(threadId)
          .text("результаты")
          .blocksAsString(blocks2));
      if (!message2.isOk()) {
        System.out.println("chat.postMessage failed: " + message2.getError());
      }
    }
    return ctx.ack();
  }

  private void postNextTaskButton(MethodsClient client, String gameRoomId, String channelId, String threadId) throws SlackApiException, IOException {
    var blocks2 = makeNextTaskButtonJson(gameRoomId);
    ChatPostMessageResponse message2 = client.chatPostMessage(r -> r
        .channel(channelId)
        .threadTs(threadId)
        .text("начать оценку")
        .blocksAsString(blocks2));
    if (!message2.isOk()) {
      System.out.println("chat.postMessage failed: " + message2.getError());
    }
  }

  private void updateMessage(MethodsClient client, String channelId, String messageId, String title, String blocks) throws SlackApiException, IOException {
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

  private Response jiraPost(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    var channel = req.getPayload().getChannel();
    var messageId = req.getPayload().getMessage().getTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();
    var finalMark = req.getPayload().getActions().get(0).getValue();

    GameRoom gameRoom = mongoDao.getGameRoomByThreadId(threadId);

    var blocks = jiraDoneJson(gameRoom, user.getUsername(), "portfolioLink");
    updateMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    // + пост в жиру

    return ctx.ack();
  }































  public Response emodzi(EventsApiPayload<ReactionAddedEvent> payload, EventContext ctx) throws SlackApiException, IOException {
    ReactionAddedEvent event = payload.getEvent();
    if (event.getReaction().equals("white_check_mark")) {
      ChatPostEphemeralResponse message = ctx.client().chatPostEphemeral(r -> r
          .channel(event.getItem().getChannel())
          .threadTs(event.getItem().getTs())
          .user(event.getUser())
          .text("<@" + event.getUser() + "> Thank you! We greatly appreciate your efforts :two_hearts:"));
      if (!message.isOk()) {
        System.out.println("chat.postMessage failed: " + message.getError());
      }
    }
    return ctx.ack();
  }




  public String jiraDoneJson(GameRoom gameRoom, String userName, String portfolioLink) {
    String roomResults = gameRoom.getTasks().stream().map(t -> t.getTitle() + " - " + t.getFinalMark()).collect(Collectors.joining("\n"));

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



  public String makeRoomEndJson(GameRoom gameRoom) {
    String roomResults = gameRoom.getTasks().stream().map(t -> t.getTitle() + " - " + t.getFinalMark()).collect(Collectors.joining("\n"));

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
        """.formatted(roomResults, gameRoom.getId(), POLL_END3);
  }

  public String makePollEndJson2(String userName, String taskTitle, List<TaskEstimation> estimations, String finalMark) {
    String pollResults = estimations.stream().map(e -> e.getUserName() + " " + e.getMark()).collect(Collectors.joining("\n"));

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

  public String makePollEndJson(String userName, List<TaskEstimation> estimations) {
    String pollResults = estimations.stream().map(e -> e.getUserName() + " " + e.getMark()).collect(Collectors.joining("\n"));

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
        """.formatted(userName, pollResults, POLL_END2);
  }

  public String makeCreateRoomModalJson(List<EstimationScale> estimationScales) {
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
                    "name": "my_name_1"
                },
                {
                    "label": "Список задач",
                    "type": "textarea",
                    "name": "my_name_2",
                    "hint": "на отдельных строчках"
                },
                {
                    "label": "Выбрать шкалу",
                    "type": "select",
                    "name": "my_name_3",
                    "options": [
        """.formatted(CREATE_ROOM_REQUEST)
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
                    "name": "my_name_4",
                    "optional": true
                }
            ]
        }
        """;
  }


  public String makeNextTaskButtonJson(String gameRoomId) {
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
        """.formatted(gameRoomId, NEXT_TASK);
  }

  public String pollJson1(String userName, String gameRoomId, String taskTitle, List<String> marks) {

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
          """.formatted(mark, mark, POLL_OPTION_1, mark)
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
        """.formatted(gameRoomId, POLL_END);
  }

  public String pollJson2(String gameRoomId, String taskTitle, List<String> marks, List<String> users) {
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
          """.formatted(mark, mark, POLL_OPTION_1, mark)
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
        """.formatted(gameRoomId, POLL_END);
  }
}
