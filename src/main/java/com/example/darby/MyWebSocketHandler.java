package com.example.darby;

import com.example.darby.wcrequest.WsRequestMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.socket_mode.response.AckResponse;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class MyWebSocketHandler implements WebSocketHandler {

  private final String token;
  private final ObjectMapper objectMapper;
  private final WebClient webClient;

  public MyWebSocketHandler(@Value("${xapp-token}") String token, ObjectMapper objectMapper, WebClient webClient) {
    this.token = token;
    this.objectMapper = objectMapper;
    this.webClient = webClient;
  }

  @Override
  public Mono<Void> handle(WebSocketSession session) {

    Mono<Void> multiplexMessageStream = session.receive()
        .log()
        .map(WebSocketMessage::getPayloadAsText)
        .map(request -> getResponse(request))
        .filter(resp -> !resp.equals(""))
        .log()
        .map(session::textMessage)
        .as(messages -> session.send(messages));

    return multiplexMessageStream;

//    Mono<WebSocketMessage> initOutMessage = Mono.just(session.textMessage("42"));
//    return session.send(initOutMessage)
//        // then/thenMany — подписаться на второго Publisher-а после окончания первого Publisher-а.
//        // наш initOutMessage это моно паблишер с 1 эвентом
//        .then(multiplexMessageStream);
  }


  public String getResponse(String req) {
    System.out.println("QQQQQQQQQQQQQQQQQQ");
    System.out.println(req);
    WsRequestMessage request = rasparsit(req, WsRequestMessage.class);


    if (request.type.equals("hello")) { // init message
      var resss = "awd";
    } else if (request.type.equals("events_api")) { // messages/reactions
      var resss = "aw3223d";
    } else if (request.type.equals("interactive")) { // shortcut
      var resss = "aw3223d";
      WsRequestMessage message = rasparsit(req, WsRequestMessage.class);
      probuemOtvetit(message);
    } else {
      var resss = "3223";
    }









    AckResponse response = new AckResponse();
    response.setEnvelopeId(request.envelope_id);

    String result = null;
    try {
      result = objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return result;
  }



  private void probuemOtvetit(WsRequestMessage request) {
//    MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
//    bodyValues.add("text", "otvet");
//    bodyValues.add("channel", request.payload.event.channel);
//    bodyValues.add("thread_ts", request.payload.event.ts);
//
//    try {
//      webClient.post()
//          .uri(new URI("https://slack.com/api/chat.postMessage"))
//          .header("Authorization", "Bearer " + token)
//          .contentType(MediaType.APPLICATION_JSON)
//          .body(BodyInserters.fromFormData(bodyValues))
//          .retrieve()
//          .bodyToMono(ChatPostMessageResponse.class)
//          .subscribe(); // .block() блокирует, так что .subscribe()
//
//    } catch (URISyntaxException e) {
//      e.printStackTrace();
//    }

    String modalJson = "{\n" +
        "  \"type\": \"modal\",\n" +
        "  \"callback_id\": \"modal-identifier\",\n" +
        "  \"title\": {\n" +
        "    \"type\": \"plain_text\",\n" +
        "    \"text\": \"Just a modal\"\n" +
        "  },\n" +
        "  \"blocks\": [\n" +
        "    {\n" +
        "      \"type\": \"section\",\n" +
        "      \"block_id\": \"section-identifier\",\n" +
        "      \"text\": {\n" +
        "        \"type\": \"mrkdwn\",\n" +
        "        \"text\": \"*Welcome* to ~my~ Block Kit _modal_!\"\n" +
        "      },\n" +
        "      \"accessory\": {\n" +
        "        \"type\": \"button\",\n" +
        "        \"text\": {\n" +
        "          \"type\": \"plain_text\",\n" +
        "          \"text\": \"Just a button\",\n" +
        "        },\n" +
        "        \"action_id\": \"button-identifier\",\n" +
        "      }\n" +
        "    }\n" +
        "  ],\n" +
        "}";

    MultiValueMap<String, String> bodyValues = new LinkedMultiValueMap<>();
    bodyValues.add("trigger_id", request.payload.trigger_id);
    bodyValues.add("dialog", modalJson);

    try {
      webClient.post()
          .uri(new URI("https://slack.com/api/dialog.open"))
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromFormData(bodyValues))
          .retrieve()
          .bodyToMono(ChatPostMessageResponse.class)
          .log()
          .subscribe();

    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  private <T> T rasparsit(String json, Class<T> cls) {
    T result;
    try {
      result = objectMapper.readValue(json, cls);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      throw new RuntimeException("ne rasparsil vhodyashee soobshenie");
    }
    return result;
  }
}

