//package com.example.darby;
//
//import com.slack.api.app_backend.events.payload.EventsApiPayload;
//import com.slack.api.bolt.App;
//import com.slack.api.bolt.context.builtin.EventContext;
//import com.slack.api.bolt.context.builtin.GlobalShortcutContext;
//import com.slack.api.bolt.request.builtin.GlobalShortcutRequest;
//import com.slack.api.bolt.response.Response;
//import com.slack.api.bolt.socket_mode.SocketModeApp;
//import com.slack.api.methods.SlackApiException;
//import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;
//import com.slack.api.methods.response.chat.ChatPostMessageResponse;
//import com.slack.api.methods.response.dialog.DialogOpenResponse;
//import com.slack.api.model.event.MessageEvent;
//import com.slack.api.model.event.ReactionAddedEvent;
//import com.slack.api.model.event.ReactionRemovedEvent;
//import java.io.IOException;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class SlackOnBolt {
//
////  implementation 'com.slack.api:bolt:1.12.1'
////  implementation 'com.slack.api:bolt-socket-mode:1.12.1'
////  implementation 'javax.websocket:javax.websocket-api:1.1'
////  implementation 'org.glassfish.tyrus.bundles:tyrus-standalone-client:1.17'
//
//  @Bean
//  public App initSlackApp() throws Exception {
//    App app = new App();
//
////    Map<Class<? extends Event>, BoltEventHandler<? extends Event>> qqq = Map.of(
////        ReactionAddedEvent.class, (payload, ctx) -> apply(payload, ctx),
////        MessageEvent.class, (payload, ctx) -> apply2(payload, ctx)
////    );
//
//    // сообщение в тред за эмодзи
//    app.event(ReactionAddedEvent.class, this::emodzi);
//    app.event(ReactionRemovedEvent.class, (payload, ctx) -> {
//      return ctx.ack();
//    });
//
//    // сообщение с кнопками за сообщение
//    // https://api.slack.com/messaging/interactivity#components
//    app.event(MessageEvent.class, this::apply2);
//
//    // обработка кликания по форме из ^
//    app.blockAction("zp10", (req, ctx) -> {
//      return ctx.ack();
//    });
//
//    // показать модал форму с тригера
//    app.globalShortcut("darby_play_id", this::modalForm);
//
//
//    new SocketModeApp(app).start();
//
//    return app;
//  }
//
//
//
//
//
//
//
//  public Response emodzi(EventsApiPayload<ReactionAddedEvent> payload, EventContext ctx) throws SlackApiException, IOException {
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
//    return ctx.ack();
//  }
//
//
//
//  public Response modalForm(GlobalShortcutRequest req, GlobalShortcutContext ctx) throws SlackApiException, IOException {
//    String modalJson = "{\n" +
//        "  \"type\": \"modal\",\n" +
//        "  \"callback_id\": \"modal-identifier\",\n" +
//        "  \"title\": {\n" +
//        "    \"type\": \"plain_text\",\n" +
//        "    \"text\": \"Just a modal\"\n" +
//        "  },\n" +
//        "  \"blocks\": [\n" +
//        "    {\n" +
//        "      \"type\": \"section\",\n" +
//        "      \"block_id\": \"section-identifier\",\n" +
//        "      \"text\": {\n" +
//        "        \"type\": \"mrkdwn\",\n" +
//        "        \"text\": \"*Welcome* to ~my~ Block Kit _modal_!\"\n" +
//        "      },\n" +
//        "      \"accessory\": {\n" +
//        "        \"type\": \"button\",\n" +
//        "        \"text\": {\n" +
//        "          \"type\": \"plain_text\",\n" +
//        "          \"text\": \"Just a button\",\n" +
//        "        },\n" +
//        "        \"action_id\": \"button-identifier\",\n" +
//        "      }\n" +
//        "    }\n" +
//        "  ],\n" +
//        "}";
//
//    DialogOpenResponse apiResponse = ctx.client().dialogOpen(r -> r
//        .triggerId(req.getPayload().getTriggerId())
//        .dialogAsString(modalJson));
//    if (!apiResponse.isOk()) {
//      System.out.println("chat.postMessage failed: " + apiResponse.getError());
//    }
//
//    return ctx.ack();
//  }
//
//
//  public Response apply2(EventsApiPayload<MessageEvent> payload, EventContext ctx) throws SlackApiException, IOException {
//    // нужно достать мету
//    // канал
//    // что за тип
//    //   сообщение с меншином
//    //   сообщение в тред с меншином
//    //   остальное скип
//    // кто написал
//    // текст
//
//    MessageEvent event = payload.getEvent();
//
//    var blocks = "[\n" +
//        "\t\t{\n" +
//        "\t\t\t\"type\": \"header\",\n" +
//        "\t\t\t\"text\": {\n" +
//        "\t\t\t\t\"type\": \"plain_text\",\n" +
//        "\t\t\t\t\"text\": \"New request\",\n" +
//        "\t\t\t\t\"emoji\": true\n" +
//        "\t\t\t}\n" +
//        "\t\t},\n" +
//        "\t\t{\n" +
//        "\t\t\t\"type\": \"section\",\n" +
//        "\t\t\t\"fields\": [\n" +
//        "\t\t\t\t{\n" +
//        "\t\t\t\t\t\"type\": \"mrkdwn\",\n" +
//        "\t\t\t\t\t\"text\": \"*Type:*\\nPaid Time Off\"\n" +
//        "\t\t\t\t},\n" +
//        "\t\t\t\t{\n" +
//        "\t\t\t\t\t\"type\": \"mrkdwn\",\n" +
//        "\t\t\t\t\t\"text\": \"*Created by:*\\n<example.com|Fred Enriquez>\"\n" +
//        "\t\t\t\t}\n" +
//        "\t\t\t]\n" +
//        "\t\t},\n" +
//        "\t\t{\n" +
//        "\t\t\t\"type\": \"section\",\n" +
//        "\t\t\t\"fields\": [\n" +
//        "\t\t\t\t{\n" +
//        "\t\t\t\t\t\"type\": \"mrkdwn\",\n" +
//        "\t\t\t\t\t\"text\": \"*When:*\\nAug 10 - Aug 13\"\n" +
//        "\t\t\t\t}\n" +
//        "\t\t\t]\n" +
//        "\t\t},\n" +
//        "\t\t{\n" +
//        "\t\t\t\"type\": \"actions\",\n" +
//        "\t\t\t\"elements\": [\n" +
//        "\t\t\t\t{\n" +
//        "\t\t\t\t\t\"type\": \"button\",\n" +
//        "\t\t\t\t\t\"text\": {\n" +
//        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
//        "\t\t\t\t\t\t\"emoji\": true,\n" +
//        "\t\t\t\t\t\t\"text\": \"Approve\"\n" +
//        "\t\t\t\t\t},\n" +
//        "\t\t\t\t\t\"style\": \"primary\",\n" +
//        "\t\t\t\t\t\"value\": \"click_me_123\"\n" +
//        "\t\t\t\t},\n" +
//        "\t\t\t\t{\n" +
//        "\t\t\t\t\t\"type\": \"button\",\n" +
//        "\t\t\t\t\t\"text\": {\n" +
//        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
//        "\t\t\t\t\t\t\"emoji\": true,\n" +
//        "\t\t\t\t\t\t\"text\": \"Reject\"\n" +
//        "\t\t\t\t\t},\n" +
//        "\t\t\t\t\t\"style\": \"danger\",\n" +
//        "\t\t\t\t\t\"value\": \"click_me_123\"\n" +
//        "\t\t\t\t}\n" +
//        "\t\t\t]\n" +
//        "\t\t}\n" +
//        "\t]";
//
//
//    if (true) {
//      ChatPostMessageResponse message = ctx.client().chatPostMessage(r -> r
//          .channel(event.getChannel())
//          .threadTs(event.getTs())
//          .text("zdarov paren")
//          .blocksAsString(blocks)
//      );
//      if (!message.isOk()) {
//        System.out.println("chat.postMessage failed: " + message.getError());
//      }
//    }
//
//    return ctx.ack();
//  }
//
//
//}
