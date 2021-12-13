package com.example.darby.service;

import com.slack.api.bolt.App;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SlackHelper {

  private final String xoxbToken;
  private final App slackApp;

  public SlackHelper(@Value("${xoxb-token}") String xoxbToken, App slackApp) {
    this.xoxbToken = xoxbToken;
    this.slackApp = slackApp;
  }

  public ChatUpdateResponse updateSlackMessage(String channelId,
                                               String messageId,
                                               String title,
                                               String blocks) throws SlackApiException, IOException {
    ChatUpdateResponse resp = slackApp.client().chatUpdate(r -> r
        .token(xoxbToken)
        .channel(channelId)
        .ts(messageId)
        .text(title)
        .blocksAsString(blocks)
    );
    if (!resp.isOk()) {
      System.out.println("chatUpdate failed: " + resp.getError());
    }

    return resp;
  }

  public Mono<ChatUpdateResponse> updateSlackMessageMono(String channelId,
                                                         String messageId,
                                                         String title,
                                                         String blocks) {
    ChatUpdateResponse resp;
    try {
      resp = slackApp.client().chatUpdate(r -> r
          .token(xoxbToken)
          .channel(channelId)
          .ts(messageId)
          .text(title)
          .blocksAsString(blocks)
      );
    } catch (IOException | SlackApiException e) {
      e.printStackTrace();
      throw new RuntimeException("Can't update message");
    }
    if (!resp.isOk()) {
      System.out.println("chatUpdate failed: " + resp.getError());
    }

    return Mono.just(resp);
  }

  public ChatPostEphemeralResponse postSlackMessageEphemeral(String channelId,
                                                             String threadId,
                                                             String slackUserId,
                                                             String text) {
    ChatPostEphemeralResponse resp;
    try {
      resp = slackApp.client().chatPostEphemeral(r -> r
          .token(xoxbToken)
          .channel(channelId)
          .threadTs(threadId)
          .user(slackUserId)
          .text(text));
    } catch (IOException | SlackApiException e) {
      e.printStackTrace();
      throw new RuntimeException("Can't send ephemeral message");
    }

    if (!resp.isOk()) {
      System.out.println("chatPostEphemeral failed: " + resp.getError());
    }

    return resp;
  }

  public ChatPostMessageResponse postSlackMessage(String channelId,
                                                  String threadId,
                                                  String title,
                                                  String blocks) throws SlackApiException, IOException {
    ChatPostMessageResponse resp = slackApp.client().chatPostMessage(r -> r
        .token(xoxbToken)
        .channel(channelId)
        .threadTs(threadId)
        .text(title)
        .blocksAsString(blocks));

    if (!resp.isOk()) {
      System.out.println("chatPostMessage failed: " + resp.getError());
    }

    return resp;
  }

  public String makePlainTextBlock(String text) {
    return """
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "%s"
            }
          }
      """.formatted(text);
  }

  public String makePlainTextMultilineBlock(List<String> lines) {
    return lines.stream().map("""
                {
                  "type": "section",
                  "text": {
                    "type": "plain_text",
                    "text": "%s"
                  }
                }
            """::formatted)
        .collect(Collectors.joining(","));
  }

  public String makeMarkDownBlock(String text) {
    return """
          {
            "type": "section",
            "text": {
              "type": "mrkdwn",
              "text": "%s"
            }
          }
      """.formatted(text);
  }

  public String makeInputBlock(String text, String actionId) {
    return """
          {
            "dispatch_action": true,
            "type": "input",
            "element": {
              "type": "plain_text_input",
              "action_id": "%s"
            },
            "label": {
              "type": "plain_text",
              "text": "%s",
            }
          }
      """.formatted(actionId, text);
  }

  public String makeDividerBlock() {
    return """
          {
            "type": "divider"
          }
      """;
  }

  public String makeButtonBlock(String text, String value, String actionId) {
    return """
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "%s",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
      """.formatted(text, value, actionId);
  }

  public String makeMarksBlock(List<String> marks, String actionIdPrefix) {
    int BATCH = 3;
    return IntStream.range(0, (marks.size() + BATCH - 1) / BATCH)
        .mapToObj(i -> marks.subList(i * BATCH, Math.min(marks.size(), (i + 1) * BATCH)))
        .map(batch ->"""
          {
            "type": "actions",
            "elements": [
          """
            +
            batch.stream()
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
              """.formatted(mark, mark, actionIdPrefix, mark)
                )
                .collect(Collectors.joining(",\n"))
            +
            """
              ]
            }
            """
        )
        .collect(Collectors.joining(",\n"));
  }
}
