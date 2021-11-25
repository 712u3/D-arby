package com.example.darby.resource;

import com.example.darby.dao.H2Dao;
import com.example.darby.service.SlackHelper;
import com.slack.api.methods.SlackApiException;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin")
public class AdminResource {

  private final H2Dao dao;
  private final SlackHelper slackHelper;

  public AdminResource(H2Dao dao, SlackHelper slackHelper) {
    this.dao = dao;
    this.slackHelper = slackHelper;
  }

  @PostMapping("/clear-db")
  public Mono<Void> getEmployeeById() {
    dao.prepareDatabase();
    return Mono.empty();
  }

  // curl -X POST -H 'Content-Type: application/json' '127.0.0.1:8082/admin/slack-message' \
  //      -d '{"channelId": "C02KN0SAQRE", "threadId": null, "text": "привет"}'
  @PostMapping(value = "/slack-message", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Void> getEmployeeById2(@RequestBody SlackMessage slackMessage) throws SlackApiException, IOException {
    if (slackMessage.channelId == null || slackMessage.text == null) {
      return Mono.empty();
    }

    slackHelper.postSlackMessage(slackMessage.channelId, slackMessage.threadId, slackMessage.text, null);

    return Mono.empty();
  }

  private static class SlackMessage {
    public String channelId;
    public String threadId;
    public String text;
  }
}
