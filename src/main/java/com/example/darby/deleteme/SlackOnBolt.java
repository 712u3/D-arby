package com.example.darby.deleteme;

import com.example.darby.dao.MongoDao;
import com.example.darby.documents.EstimationScale;
import com.example.darby.documents.GameRoom;
import com.example.darby.documents.RoomLocation;
import com.example.darby.documents.SlackChannel;
import com.example.darby.documents.SlackUser;
import com.example.darby.documents.Task;
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
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class SlackOnBolt {
  private static final String CREATE_ROOM_MODAL = "darby_play_id";
  private static final String CREATE_ROOM_REQUEST = "create_room_request";
  private static final String START_GAME = "start_game_id";
  private static final String POLL_OPTION_1 = "actionId-31231";
  private static final String POLL_OPTION_2 = "actionId-222";
  private static final String POLL_OPTION_3 = "actionId-111";
  private static final String POLL_END = "actionId-1212";

  private SocketModeApp socketApp;
  private final MongoDao mongoDao;

  public SlackOnBolt(MongoDao mongoDao) {
    this.mongoDao = mongoDao;
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
    app.globalShortcut(CREATE_ROOM_MODAL, this::createRoomModal);
    app.dialogSubmission(CREATE_ROOM_REQUEST, this::createRoomRequest);
    app.blockAction(START_GAME, this::startGame);
    app.blockAction(POLL_OPTION_1, this::pollEvent);
    app.blockAction(POLL_OPTION_2, this::pollEvent);
    app.blockAction(POLL_OPTION_3, this::pollEvent);
    app.blockAction(POLL_END, this::pollEvent);

    socketApp = new SocketModeApp(app);
    socketApp.start();
  }

//  @PreDestroy
  public void shutdown() throws Exception {
    socketApp.stop();
  }



  public Response createRoomModal(GlobalShortcutRequest req, GlobalShortcutContext ctx) throws SlackApiException, IOException {
    //TODO подтянуть последнюю использовавшуюся шкалу в этом канале
    String modalJson = makeCreateRoomModalJson();

    DialogOpenResponse apiResponse = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modalJson));
    if (!apiResponse.isOk()) {
      System.out.println("chat.postMessage failed: " + apiResponse.getError());
      System.out.println(apiResponse.getResponseMetadata());
    }

    return ctx.ack();
  }


  private Response createRoomRequest(DialogSubmissionRequest req, DialogSubmissionContext ctx) throws SlackApiException, IOException {
    DialogSubmissionPayload.Channel channel = req.getPayload().getChannel();
    DialogSubmissionPayload.User user = req.getPayload().getUser();
    Map<String, String> data = req.getPayload().getSubmission();
    String title = data.get("my_name_1");
    String zadachi = data.get("my_name_2");
    String shkala = data.get("my_name_3");

    // надо сохранить в базу сущнсоть канал (чтоб потом айди знать) если у нас такого еще нет
    String slackChannelId = mongoDao.upsert(new SlackChannel(channel.getId(), channel.getName()));
    // надо сохранить в базу сущнсоть юзер (чтоб потом айди знать)
    mongoDao.upsert(new SlackUser(user.getId(), user.getName()));
    // надо сохранить в базу сущнсоть шкала (если новая)
    String estimationScaleId = mongoDao.upsert(new EstimationScale(List.of(shkala.split(", "))));

    ChatPostMessageResponse message = ctx.client().chatPostMessage(r -> r
        .channel(channel.getId())
        .text("тредик для оценки " + title));
    if (!message.isOk()) {
      System.out.println("chat.postMessage failed: " + message.getError());
    }
    var messageTs = message.getTs();

    // надо сохранить в базу сущнсоть комната
    RoomLocation roomLocation = mongoDao.save(new RoomLocation(slackChannelId, messageTs));
    GameRoom gameRoom = mongoDao.save(new GameRoom(title, estimationScaleId, roomLocation.getId()));
    int index = 0;
    for(String zadacha : zadachi.split("\n")) {
      mongoDao.save(new Task(gameRoom.getId(), zadacha, index++));
    }

    var blocks = makePlayButtonJson();

    ChatPostMessageResponse message2 = ctx.client().chatPostMessage(r -> r
        .channel(channel.getId())
        .threadTs(messageTs)
        .text("начать игру")
        .blocksAsString(blocks));
    if (!message2.isOk()) {
      System.out.println("chat.postMessage failed: " + message2.getError());
    }

    return ctx.ack();
  }

  private Response startGame(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    var channelId = req.getPayload().getContainer().getChannelId();
    var messageTs = req.getPayload().getContainer().getMessageTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();

    var updBlocks = playButtonPressedJson(user.getName());
    var updMessage = ctx.client().chatUpdate(r -> r
        .channel(channelId)
        .ts(messageTs)
        .text("начинает")
        .blocksAsString(updBlocks)
    );
    if (!updMessage.isOk()) {
      System.out.println("chat.postMessage failed: " + updMessage.getError());
    }

    var blocks = pollJson(List.of());

    ChatPostMessageResponse message = ctx.client().chatPostMessage(r -> r
        .channel(channelId)
        .threadTs(threadId)
        .text("голосование")
        .blocksAsString(blocks));
    if (!message.isOk()) {
      System.out.println("chat.postMessage failed: " + message.getError());
      System.out.println(message.getResponseMetadata());
    }

    return ctx.ack();
  }

  private Response pollEvent(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    var channel = req.getPayload().getChannel();
    var messageTs = req.getPayload().getMessage().getTs();
    var threadId = req.getPayload().getMessage().getThreadTs();
    var user = req.getPayload().getUser();
    var selected = req.getPayload().getActions().get(0).getSelectedOption();

    var updBlocks = pollJson(List.of(user.getName()));
    var updMessage = ctx.client().chatUpdate(r -> r
        .channel(channel.getId())
        .ts(messageTs)
        .text("проголосовал")
        .blocksAsString(updBlocks)
    );
    if (!updMessage.isOk()) {
      System.out.println("chat.postMessage failed: " + updMessage.getError());
    }

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














  public String makeCreateRoomModalJson() {
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
                        {
                          "label": "первый вариант: 1,2,3,4",
                          "value": "select1"
                        },
                        {
                          "label": "второй вариант 4,5,6,7",
                          "value": "select2"
                        },
                        {
                          "label": "своя шкала",
                          "value": "select3"
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
        """.formatted(CREATE_ROOM_REQUEST);
  }


  public String makePlayButtonJson() {
    return """
        [
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Начать игру",
                  "emoji": true
                },
                "value": "qqq",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(START_GAME);
  }

  public String playButtonPressedJson(String userName) {
    return """
        [
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "<@%s нажимает",
              "emoji": true
            }
          }
        ]
        """.formatted(userName);
  }

  public String pollJson(List<String> users) {
    String usersDone = "";
    if (users.size() > 0) {
      usersDone = """
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            }
          },
      """.formatted("завершили:\n" + users.get(0));
    }

    return """
        [
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Farmhouse",
                  "emoji": true
                },
                "value": "click_me_123",
                "action_id": "%s"
              },
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Kin Khao",
                  "emoji": true
                },
                "value": "click_me_123",
                "action_id": "%s"
              },
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Ler Ros",
                  "emoji": true
                },
                "value": "click_me_123",
                "action_id": "%s"
              }
            ]
          },
          {
            "type": "divider"
          },
        """.formatted(POLL_OPTION_1, POLL_OPTION_2, POLL_OPTION_3)
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
                  "text": "Закончить",
                  "emoji": true
                },
                "value": "click_me_123",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(POLL_END);
  }
}
