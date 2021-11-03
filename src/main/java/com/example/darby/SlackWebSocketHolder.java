package com.example.darby;

import com.example.darby.dto.WsTicketResponse;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

@Component
public class SlackWebSocketHolder {

  private final String xappToken;
  private final WebClient webClient;
  private final WebSocketClient webSocketClient;
  private final SlackRequestHandler slackRequestHandler;

  public SlackWebSocketHolder(@Value("${xapp-token}") String xappToken,
                              WebClient webClient,
                              WebSocketClient webSocketClient,
                              SlackRequestHandler slackRequestHandler) {
    this.xappToken = xappToken;
    this.webClient = webClient;
    this.webSocketClient = webSocketClient;
    this.slackRequestHandler = slackRequestHandler;
  }

  // TODO add logging and reconnect
  @PostConstruct
  private void initBoughtResumeStatsLogger() throws URISyntaxException {
    webClient.post()
        .uri(new URI("https://slack.com/api/apps.connections.open"))
        .header("Authorization", "Bearer " + xappToken)
        .contentType(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(WsTicketResponse.class)
        .mapNotNull(this::makeUri)
        .flatMap(wsUri -> webSocketClient.execute(wsUri, slackRequestHandler))
        .subscribe();
  }

  private URI makeUri(WsTicketResponse wsTicketResponse) {
    try {
      return new URI(wsTicketResponse.url);
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return null;
  }

}
