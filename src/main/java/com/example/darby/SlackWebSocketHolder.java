package com.example.darby;

import com.slack.api.methods.response.rtm.RTMConnectResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

@Component
public class SlackWebSocketHolder {

  private final String token;
  private final WebClient webClient;
  private final WebSocketClient webSocketClient;
  private final SlackRequestHandler myWebSocketHandler;

  public SlackWebSocketHolder(@Value("${xapp-token}") String token,
                              WebClient webClient,
                              WebSocketClient webSocketClient,
                              SlackRequestHandler myWebSocketHandler) {
    this.token = token;
    this.webClient = webClient;
    this.webSocketClient = webSocketClient;
    this.myWebSocketHandler = myWebSocketHandler;
  }

  // он тут нах не нужен, надо избавиться
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  @PostConstruct
  private void initBoughtResumeStatsLogger() {
    executorService.submit(() -> {
      try {
        allWsActions();
      } catch (URISyntaxException e) {
        e.printStackTrace(); // этого никогда не случится
      }
    });
  }

  @PreDestroy
  public void destroy() {
    System.out.println("Callback triggered - @PreDestroy.");
    executorService.shutdown();
  }

  // здесь будет вся вебсокет хуйня
  // TODO прикрутить логирование и реконнекты
  public void allWsActions() throws URISyntaxException {
    // надо отправить постзапрос и получить тикет
    RTMConnectResponse response = webClient.post()
        .uri(new URI("https://slack.com/api/apps.connections.open"))
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(RTMConnectResponse.class)
        .block();

    if (response == null || !response.isOk()) {
      throw new IllegalStateException("Failed to connect to the RTM endpoint URL (error: " + response.getError() + ")");
    }
    URI wsUri = new URI(response.getUrl());


    // далее с тикетом заебашить вебсокет
    webSocketClient.execute(wsUri, myWebSocketHandler).block(); // Duration.ofSeconds(120L)
    // к этому времени уже рождено 3 треда!!! wtf
  }
}
