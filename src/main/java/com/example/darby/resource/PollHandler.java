package com.example.darby.resource;

import com.example.darby.dao.H2Dao;
import com.example.darby.entity.EstimationScale;
import com.example.darby.entity.GameRoom;
import com.example.darby.entity.HhUser;
import com.example.darby.entity.Task;
import com.example.darby.entity.TaskEstimation;
import com.example.darby.service.JiraHelper;
import com.example.darby.service.SlackHelper;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PollHandler {
  public static final String NEXT_TASK_EVENT = "next_task_event";
  public static final String POLL_OPTION_SELECTED_EVENT = "poll_option_selected";
  public static final String POLL_ENDED_EVENT = "poll_ended_event";
  public static final Pattern POLL_OPTION_SELECTED_PATTERN = Pattern.compile("^" + POLL_OPTION_SELECTED_EVENT + "-.*$");
  public static final String TASK_ESTIMATED_EVENT = "task_estimated_event";
  public static final String CREATE_JIRA_ISSUE_EVENT = "create_jira_issue_event";

  private final H2Dao dao;
  private final SlackHelper slackHelper;
  private final JiraHelper jiraHelper;

  public PollHandler(H2Dao dao, SlackHelper slackHelper, JiraHelper jiraHelper) {
    this.dao = dao;
    this.slackHelper = slackHelper;
    this.jiraHelper = jiraHelper;
  }

  public Response handleStartTaskPoll(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    String channelId = req.getPayload().getContainer().getChannelId();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();
    String messageId = req.getPayload().getContainer().getMessageTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoom(Integer.valueOf(gameRoomId));
    EstimationScale estimationScale = dao.getEstimationScale(gameRoom.getEstimationScaleId());
    // таска должна быть всегда тк
    // в первый раз таска точно есть,
    // в остальные разы постим кнопку только если есть
    Task currentTask = dao.getCurrentTask(Integer.valueOf(gameRoomId));
    currentTask.setMessageId(messageId);
    dao.updateTaskField(currentTask.getId(), "message_id", messageId);

    updateTaskMessage(currentTask, estimationScale.getMarks(), channelId, slackUser.getUsername(), List.of());

    return ctx.ack();
  }

  private String makePollStartedBody(String userName, Integer gameRoomId, String taskTitle, List<String> marks) {
    String taskTitle1 = taskTitle;
    if (userName != null) {
      taskTitle1 = "(" + userName + ")\n" + taskTitle;
    }

    return "[" +
        slackHelper.makePlainTextBlock(taskTitle1) + "," +
        slackHelper.makeMarksBlock(marks, POLL_OPTION_SELECTED_EVENT) + "," +
        slackHelper.makeDividerBlock() + "," +
        slackHelper.makeButtonBlock("Закончить голосование", String.valueOf(gameRoomId), POLL_ENDED_EVENT) +
        "]";
  }

  public Response handlePollSelect(BlockActionRequest req, ActionContext ctx) throws SlackApiException, IOException {
    BlockActionPayload.Channel slackChannel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String selected = req.getPayload().getActions().get(0).getValue();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoomByThreadId(threadId);
    EstimationScale estimationScale = dao.getEstimationScale(gameRoom.getEstimationScaleId());
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    dao.updateOrSaveTaskEstimation(currentTask.getId(), slackUser.getUsername(), selected);

    List<TaskEstimation> taskEstimations = dao.getTaskEstimations(currentTask.getId());

    updateTaskMessage(currentTask, estimationScale.getMarks(), slackChannel.getId(), slackUser.getUsername(),
        taskEstimations);

    return ctx.ack();
  }

  private String malePollUpdatedBody(Integer gameRoomId, String taskTitle, List<String> marks, List<String> users) {
    String usersDone = "Нажали (" + users.size() + "):\n" + String.join("\n", users);

    return "[" +
        slackHelper.makePlainTextBlock(taskTitle) + "," +
        slackHelper.makeMarksBlock(marks, POLL_OPTION_SELECTED_EVENT) + "," +
        slackHelper.makeDividerBlock() + "," +
        slackHelper.makePlainTextBlock(usersDone) + "," +
        slackHelper.makeButtonBlock("Завершить голосование", String.valueOf(gameRoomId), POLL_ENDED_EVENT) +
        "]";
  }

  public Response handlePollStop(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel slackChannel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoom(Integer.valueOf(gameRoomId));
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    currentTask.setStopped(true);
    dao.updateTaskField(currentTask.getId(), "stopped", true);
    List<TaskEstimation> taskEstimations = dao.getTaskEstimations(currentTask.getId());

    updateTaskMessage(currentTask, List.of(), slackChannel.getId(), slackUser.getUsername(), taskEstimations);

    return ctx.ack();
  }

  private String makePollEndedBody(String userName, String taskTitle, List<TaskEstimation> estimations) {
    String pollResults = estimations.stream()
        .map(e -> e.getSlackUserName() + " " + e.getMark())
        .collect(Collectors.joining("\n"));

    return "[" +
        slackHelper.makePlainTextBlock("(%s) завершает голосование".formatted(userName)) + "," +
        slackHelper.makePlainTextBlock(taskTitle) + "," +
        slackHelper.makePlainTextBlock("Результаты:\n%s".formatted(pollResults)) + "," +
        slackHelper.makeInputBlock("Итоговая оценка", TASK_ESTIMATED_EVENT) + "," +
        "]";
  }

  public Response handleTaskDone(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel slackChannel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String finalMark = req.getPayload().getActions().get(0).getValue();

    dao.saveUserIfNotExists(new HhUser(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = dao.getGameRoomByThreadId(threadId);
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    currentTask.setFinalMark(finalMark);
    dao.updateTaskField(currentTask.getId(), "final_mark", finalMark);
    List<TaskEstimation> taskEstimations = dao.getTaskEstimations(currentTask.getId());

    updateTaskMessage(currentTask, List.of(), slackChannel.getId(), slackUser.getUsername(), taskEstimations);

    prepareNextTask(gameRoom, slackChannel.getId(), threadId);

    return ctx.ack();
  }

  public void prepareNextTask(GameRoom gameRoom,
                              String slackChannelId,
                              String threadId) throws SlackApiException, IOException {
    Task currentTask = dao.getCurrentTask(gameRoom.getId());
    if (currentTask != null) {
      slackHelper.postSlackMessage(slackChannelId, threadId, "Начать оценку", makeNextTaskButtonBody(gameRoom.getId()));
    } else {
      List<Task> tasks = dao.getGameRoomTasks(gameRoom.getId());
      String blocks = makeGameRoomEndedBody(gameRoom.getId(), tasks);
      gameRoom.setEnded(true);
      dao.updateGameRoomField(gameRoom.getId(), "ended", true);
      slackHelper.postSlackMessage(slackChannelId, threadId, "Результаты", blocks);
    }
  }

  private String makeTaskEndedBody(
      String userName,
      String taskTitle,
      List<TaskEstimation> estimations,
      String finalMark
  ) {
    String pollResults = estimations.stream()
        .map(e -> e.getSlackUserName() + " " + e.getMark())
        .collect(Collectors.joining("\n"));

    return "[" +
        slackHelper.makePlainTextBlock("(%s) завершает голосование".formatted(userName)) + "," +
        slackHelper.makePlainTextBlock(taskTitle) + "," +
        slackHelper.makePlainTextBlock("Результаты:\n%s".formatted(pollResults)) + "," +
        slackHelper.makePlainTextBlock("Итоговая оценка: %s".formatted(finalMark)) + "," +
        "]";
  }

  private String makeNextTaskButtonBody(Integer gameRoomId) {
    return "[" +
        slackHelper.makeButtonBlock("Следующая задача", String.valueOf(gameRoomId), NEXT_TASK_EVENT) +
        "]";
  }

  private String makeGameRoomEndedBody(Integer gameRoomId, List<Task> tasks) {
    String roomResults = tasks.stream()
        .map(t -> t.getTitle() + " - " + t.getFinalMark())
        .collect(Collectors.joining("\n"));

    Optional<Integer> sumStoryPointsOpt = jiraHelper.makeSumStoryPoints(tasks);
    String sumStoryPoints = sumStoryPointsOpt.map(s -> " (" + s + ")").orElse("");

    return "[" +
        slackHelper.makePlainTextBlock("Результаты%s:".formatted(sumStoryPoints)) + "," +
        slackHelper.makePlainTextBlock(roomResults) + "," +
        slackHelper.makeButtonBlock("Запостить в жиру", String.valueOf(gameRoomId), CREATE_JIRA_ISSUE_EVENT) +
        "]";
  }

  // этот метод нужен потому что когда мы меняем описание задачи, нам надо обновить сообщение
  // которое может быть в любом состоянии
  public void updateTaskMessage(Task task, List<String> marks, String channelId, String userName,
                                List<TaskEstimation> taskEstimations) throws SlackApiException, IOException {
    if (task.getDeleted()) {
      String updBlocks = "[" + slackHelper.makePlainTextBlock("(удалено) " + task.getTitle()) + "]";
      slackHelper.updateSlackMessage(channelId, task.getMessageId(), "Голосование", updBlocks);
    } else if (!task.getStopped() && marks.size() == 0) {
      // 1 Только началось, еще никто не голосовал
      //   но если в другом потоке проголосует то это провал
      String updBlocks = makePollStartedBody(userName, task.getGameRoomId(), task.getTitle(), marks);
      slackHelper.updateSlackMessage(channelId, task.getMessageId(), "Голосование", updBlocks);
    } else if (!task.getStopped() && marks.size() > 0) {
      // 2 Инпрогресс, есть голоса
      List<String> usersDone = taskEstimations.stream()
          .map(TaskEstimation::getSlackUserName).collect(Collectors.toList());
      String updBlocks = malePollUpdatedBody(task.getGameRoomId(), task.getTitle(), marks, usersDone);
      slackHelper.updateSlackMessage(channelId, task.getMessageId(), "Голосование", updBlocks);
    } else if (task.getStopped() && task.getFinalMark() == null) {
      // 3 Голосование остановлено, выбирается финальная оценка
      String blocks = makePollEndedBody(userName, task.getTitle(), taskEstimations);
      slackHelper.updateSlackMessage(channelId, task.getMessageId(), "Голосование", blocks);
    } else if (task.getStopped() && task.getFinalMark() != null) {
      // 4 Голосование завершено
      String blocks = makeTaskEndedBody(userName, task.getTitle(), taskEstimations, task.getFinalMark());
      slackHelper.updateSlackMessage(channelId, task.getMessageId(), "Голосование", blocks);
    }
  }
}
