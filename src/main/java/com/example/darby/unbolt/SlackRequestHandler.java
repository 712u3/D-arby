//package com.example.darby;
//
//import com.example.darby.dto.Acknowledge;
//import com.example.darby.dto.WsRequestMessage;
//import com.example.darby.dto.wsrequest.ApiEventRequest;
//import com.example.darby.dto.wsrequest.CommonWsRequest;
//import com.example.darby.dto.wsrequest.HelloRequest;
//import com.example.darby.dto.wsrequest.InteractionRequest;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.slack.api.methods.response.chat.ChatPostMessageResponse;
//import java.net.URI;
//import java.net.URISyntaxException;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Component;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.reactive.function.BodyInserters;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.socket.WebSocketHandler;
//import org.springframework.web.reactive.socket.WebSocketMessage;
//import org.springframework.web.reactive.socket.WebSocketSession;
//import reactor.core.publisher.Mono;
//
//@Component
//public class SlackRequestHandler implements WebSocketHandler {
//
//  private final String xoxbToken;
//  private final ObjectMapper objectMapper;
//  private final WebClient webClient;
//  private final ReactiveMongoTemplate reactiveMongoTemplate;
//
//  public SlackRequestHandler(@Value("${xoxb-token}") String xoxbToken,
//                             ObjectMapper objectMapper,
//                             WebClient webClient,
//                             ReactiveMongoTemplate reactiveMongoTemplate) {
//    this.xoxbToken = xoxbToken;
//    this.objectMapper = objectMapper;
//    this.webClient = webClient;
//    this.reactiveMongoTemplate = reactiveMongoTemplate;
//  }
//
//  @Override
//  public Mono<Void> handle(WebSocketSession session) {
//    return session.receive()
//        .log()
//        .map(WebSocketMessage::getPayloadAsText)
//        .mapNotNull(wsRequest -> getResponse(wsRequest))
//        .map(session::textMessage)
//        .as(messages -> session.send(messages));
//  }
//
//
//  public String getResponse(String req) {
//    System.out.println("QQQQQQQQQQQQQQQQQQ");
//    System.out.println(req);
//    WsRequestMessage request = rasparsit(req, WsRequestMessage.class);
//
//    CommonWsRequest wsRequest = rasparsit(req, CommonWsRequest.class);
//    if (wsRequest instanceof InteractionRequest wsRequest2) {
//      processWsRequest(wsRequest2);
//    } else if (wsRequest instanceof HelloRequest) {
//      processWsRequest((HelloRequest) wsRequest);
//    } else if (wsRequest instanceof ApiEventRequest) {
//      processWsRequest((ApiEventRequest) wsRequest);
//    }
//
//    if (request.type.equals("hello")) { // init message
//    } else if (request.type.equals("events_api")) { // messages/reactions
//    } else if (request.type.equals("interactive")) { // shortcut
//      if ("shortcut".equals(request.payload.type) && "darby_play_id".equals(request.payload.callback_id)) {
//        sendDialogForm(request);
//      } else if ("dialog_submission".equals(request.payload.type) && "create_room_id".equals(request.payload.callback_id)) {
//        // тогда создаем в базе комнату со всеми вводными
//        // вводные:
//        // канал, айди сообщения, айди автора, портфель с здачами, шкала
//        // + постим сообщение "тредик для оценки портфеля х"
//      }
//    } else {
//    }
//
//    return getAckResponse(request);
//  }
//
//  private void processWsRequest(HelloRequest helloRequest) {
//  }
//
//  private void processWsRequest(ApiEventRequest apiEventRequest) {
//
//  }
//
//  private void processWsRequest(InteractionRequest interactionRequest) {
//
//  }
//
//
//  private void sendDialogForm(WsRequestMessage request) {
//    //  https://api.slack.com/dialogs#select_elements
//    String modalJson = "{\n" +
//        "    \"callback_id\": \"create_room_id\",\n" +
//        "    \"title\": \"Создать комнату\",\n" +
//        "    \"submit_label\": \"Создать\",\n" +
//        "    \"elements\": [\n" +
//        "        {\n" +
//        "            \"label\": \"Ссылка на портфель\",\n" +
//        "            \"type\": \"text\",\n" +
//        "            \"name\": \"my_name_1\"\n" +
//        "        },\n" +
//        "        {\n" +
//        "            \"label\": \"Список задач\",\n" +
//        "            \"type\": \"textarea\",\n" +
//        "            \"name\": \"my_name_2\",\n" +
//        "            \"hint\": \"на отдельных строчках\"\n" +
//        "        },\n" +
//        "        {\n" +
//        "            \"label\": \"Выбрать шкалу\",\n" +
//        "            \"type\": \"select\",\n" +
//        "            \"name\": \"my_name_3\",\n" +
//        "            \"options\": [\n" +
//        "                {\n" +
//        "                  \"label\": \"первый вариант: 1,2,3,4\",\n" +
//        "                  \"value\": \"select1\"\n" +
//        "                },\n" +
//        "                {\n" +
//        "                  \"label\": \"второй вариант 4,5,6,7\",\n" +
//        "                  \"value\": \"select2\"\n" +
//        "                },\n" +
//        "                {\n" +
//        "                  \"label\": \"своя шкала\",\n" +
//        "                  \"value\": \"select3\"\n" +
//        "                } \n" +
//        "              ]\n" +
//        "        },\n" +
//        "        {\n" +
//        "            \"label\": \"Своя шкала\",\n" +
//        "            \"type\": \"text\",\n" +
//        "            \"name\": \"my_name_4\",\n" +
//        "            \"optional\": true\n" +
//        "        }\n" +
//        "    ]\n" +
//        "}";
//
//    MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
//    bodyValues.add("trigger_id", request.payload.trigger_id);
//    bodyValues.add("dialog", modalJson);
//
//    try {
//      webClient.post()
//          .uri(new URI("https://slack.com/api/dialog.open"))
//          .header("Authorization", "Bearer " + xoxbToken)
//          .contentType(MediaType.APPLICATION_JSON)
//          .body(BodyInserters.fromFormData(bodyValues))
//          .retrieve()
//          .bodyToMono(ChatPostMessageResponse.class)
//          .log()
//          .subscribe();
//
//    } catch (URISyntaxException e) {
//      e.printStackTrace();
//    }
//  }
//
//  private <T> T rasparsit(String json, Class<T> cls) {
//    T result;
//    try {
//      result = objectMapper.readValue(json, cls);
//    } catch (JsonProcessingException e) {
//      e.printStackTrace();
//      throw new RuntimeException("ne rasparsil vhodyashee soobshenie");
//    }
//    return result;
//  }
//
//  private String getAckResponse(WsRequestMessage request) {
//    Acknowledge response = new Acknowledge(request.envelope_id);
//
//    String result = null;
//    try {
//      result = objectMapper.writeValueAsString(response);
//    } catch (JsonProcessingException e) {
//      e.printStackTrace();
//    }
//    return result;
//  }
//
//
////    MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
////    bodyValues.add("text", "otvet");
////    bodyValues.add("channel", request.payload.event.channel);
////    bodyValues.add("thread_ts", request.payload.event.ts);
////
////    try {
////      webClient.post()
////          .uri(new URI("https://slack.com/api/chat.postMessage"))
////          .header("Authorization", "Bearer " + token)
////          .contentType(MediaType.APPLICATION_JSON)
////          .body(BodyInserters.fromFormData(bodyValues))
////          .retrieve()
////          .bodyToMono(ChatPostMessageResponse.class)
////          .subscribe(); // .block() блокирует, так что .subscribe()
////
////    } catch (URISyntaxException e) {
////      e.printStackTrace();
////    }
//
//
//
////    reactiveMongoTemplate.save(new User(null, "Bill", 12.3)).block();
////    User aa = reactiveMongoTemplate.findAll(User.class).blockLast();
////    System.out.println("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ");
////    System.out.println(aa.getId());
////    System.out.println(aa.getOwner());
////    System.out.println(aa.getValue());
//}
//
