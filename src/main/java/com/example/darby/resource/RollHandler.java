package com.example.darby.resource;

import com.slack.api.app_backend.dialogs.payload.DialogSubmissionPayload;
import com.slack.api.bolt.context.builtin.DialogSubmissionContext;
import com.slack.api.bolt.context.builtin.GlobalShortcutContext;
import com.slack.api.bolt.request.builtin.DialogSubmissionRequest;
import com.slack.api.bolt.request.builtin.GlobalShortcutRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.dialog.DialogOpenResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RollHandler {
  public static final String ROLL_SHORTCUT = "roll_shortcut";
  public static final String ROLL_EVENT = "roll_event";

  public Response handleRollShortcut(
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

  public Response handleRollEvent(
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
