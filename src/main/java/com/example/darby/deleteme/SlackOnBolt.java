package com.example.darby.deleteme;

import com.example.darby.dao.MongoDao;
import com.example.darby.documents.EstimationScale;
import com.example.darby.documents.GameRoom;
import com.example.darby.documents.Task;
import com.example.darby.documents.TaskEstimation;
import com.example.darby.documents.User;
import com.example.darby.dto.CrabTeam;
import com.example.darby.dto.JiraIssuesCreated;
import com.slack.api.app_backend.dialogs.payload.DialogSubmissionPayload;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
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
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.dialog.DialogOpenResponse;
import com.slack.api.model.event.ReactionAddedEvent;
import com.slack.api.model.event.ReactionRemovedEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SlackOnBolt {
  private static final String CREATE_ROOM_MODAL = "darby_play_id"; // set in interface
  private static final String CREATE_ROOM_REQUEST_EVENT = "create_room_request_event";
  private static final String NEXT_TASK_EVENT = "next_task_event";
  private static final String POLL_OPTION_SELECTED_EVENT = "poll_option_selected";
  private static final String POLL_ENDED_EVENT = "poll_ended_event";
  private static final String TASK_ESTIMATED_EVENT = "task_estimated_event";
  private static final String CREATE_JIRA_ISSUE_EVENT = "create_jira_issue_event";

  private final Pattern PORTFOLIO_PATTERN_LONG = Pattern.compile("^https://jira\\.hh\\.ru/browse/(PORTFOLIO-\\d+)$");
  private final Pattern PORTFOLIO_PATTERN_SHORT = Pattern.compile("^(PORTFOLIO-\\d+)$");
  private final Pattern POLL_OPTION_SELECTED_PATTERN = Pattern.compile("^" + POLL_OPTION_SELECTED_EVENT + "-\\w+$");

  private SocketModeApp socketApp;
  private final MongoDao mongoDao;
  private final WebClient webClient;
  private final String jiraToken;

  public SlackOnBolt(MongoDao mongoDao,
                     WebClient webClient,
                     @Value("${jira-username}") String jiraUsername,
                     @Value("${jira-password}") String jiraPassword) {
    this.mongoDao = mongoDao;
    this.webClient = webClient;

    String jiraCred = jiraUsername + ":" + jiraPassword;
    this.jiraToken = Base64.getEncoder().encodeToString(jiraCred.getBytes());
  }

  @Scheduled(initialDelay = 1000, fixedDelay=Long.MAX_VALUE)
  public void init() throws Exception {
    App app = new App();

    // stub
    app.event(ReactionAddedEvent.class, this::emodzi);
    app.event(ReactionRemovedEvent.class, (payload, ctx) -> ctx.ack());

    // portfolio flow
    app.globalShortcut(CREATE_ROOM_MODAL, this::handleCreateRoomShortcut);
    app.dialogSubmission(CREATE_ROOM_REQUEST_EVENT, this::handleCreateGameRoom);
    app.blockAction(NEXT_TASK_EVENT, this::handleNextTask);
    app.blockAction(POLL_OPTION_SELECTED_PATTERN, this::handlePollSelect);
    app.blockAction(POLL_ENDED_EVENT, this::handlePollStop);
    app.blockAction(TASK_ESTIMATED_EVENT, this::handleTaskDone);
    app.blockAction(CREATE_JIRA_ISSUE_EVENT, this::handleJiraButton);

    socketApp = new SocketModeApp(app);
    socketApp.start();
  }

  @PreDestroy
  public void shutdown() throws Exception {
    socketApp.stop();
  }

  public Response handleCreateRoomShortcut(
      GlobalShortcutRequest req,
      GlobalShortcutContext ctx
  ) throws SlackApiException, IOException {
    List<EstimationScale> estimationScales = mongoDao.getAllEstimationScales(req.getPayload().getUser().getId());
    String modal = makeRoomCreationModalBody(estimationScales);

    DialogOpenResponse response = ctx.client().dialogOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .dialogAsString(modal));

    if (!response.isOk()) {
      System.out.println("dialogOpen failed: " + response.getError());
    }

    return ctx.ack();
  }

  private Response handleCreateGameRoom(
      DialogSubmissionRequest req,
      DialogSubmissionContext ctx
  ) throws SlackApiException, IOException {
    DialogSubmissionPayload.Channel slackChannel = req.getPayload().getChannel();
    DialogSubmissionPayload.User slackUser = req.getPayload().getUser();
    Map<String, String> formData = req.getPayload().getSubmission();
    String portfolioKey = extractPortfolioKey(formData.get("portfolio_key")).orElseThrow();
    String tasksText = formData.get("tasks_text");
    String estimationScaleId = formData.get("estimation_scale_id");
    String newEstimationScale = formData.get("new_estimation_scale");

    mongoDao.saveUserIfNotExists(new User(slackUser.getId(), slackUser.getName()));

    if (estimationScaleId.equals("new_scale")) {
      EstimationScale estimationScale = new EstimationScale("Своя шкала", List.of(newEstimationScale.split("[\\s,]+")));
      estimationScaleId = mongoDao.getEstimationScaleOrSave(estimationScale);
    }

    ChatPostMessageResponse response = ctx.client().chatPostMessage(r -> r
        .channel(slackChannel.getId())
        .text("Тредик для оценки " + portfolioKey));
    if (!response.isOk()) {
      System.out.println("chatPostMessage failed: " + response.getError());
      return ctx.ack();
    }
    String threadId = response.getTs();

    List<Task> tasks = tasksText.lines().map(Task::new).collect(Collectors.toList());
    GameRoom gameRoom = new GameRoom(portfolioKey, estimationScaleId, slackUser.getId(), slackChannel.getId(), threadId,
        tasks);
    String gameRoomId = mongoDao.save(gameRoom).getId();

    postNextTaskButton(ctx.client(), gameRoomId, slackChannel.getId(), threadId);

    return ctx.ack();
  }

  private Response handleNextTask(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    String channelId = req.getPayload().getContainer().getChannelId();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();
    String messageId = req.getPayload().getContainer().getMessageTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();

    mongoDao.saveUserIfNotExists(new User(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = mongoDao.getGameRoom(gameRoomId);
    EstimationScale estimationScale = mongoDao.getEstimationScale(gameRoom.getEstimationScaleId());
    Task nextTask = gameRoom.getNextTask()
        .get(); // в первый раз таска есть, в остальные разы постим только если есть некст

    String updBlocks = makePollStartedBody(slackUser.getUsername(), gameRoomId, nextTask.getTitle(),
        estimationScale.getMarks());
    updateSlackMessage(ctx.client(), channelId, messageId, "голосование", updBlocks);

    return ctx.ack();
  }

  private Response handlePollSelect(BlockActionRequest req, ActionContext ctx) throws SlackApiException, IOException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String selected = req.getPayload().getActions().get(0).getValue();

    mongoDao.saveUserIfNotExists(new User(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = mongoDao.getGameRoomByThreadId(threadId);
    EstimationScale estimationScale = mongoDao.getEstimationScale(gameRoom.getEstimationScaleId());
    Task currentTask = gameRoom.getNextTask().get();

    // race condition?
    Optional<TaskEstimation> lastUserEstimationOpt = currentTask.getEstimations().stream()
        .filter(e -> e.getUserName().equals(slackUser.getUsername()))
        .findFirst();
    if (lastUserEstimationOpt.isEmpty()) {
      currentTask.getEstimations().add(new TaskEstimation(slackUser.getUsername(), selected));
    } else {
      lastUserEstimationOpt.get().setMark(selected);
    }
    mongoDao.save(gameRoom);

    List<String> usersDone = currentTask.getEstimations().stream()
        .map(TaskEstimation::getUserName)
        .filter(userName -> !userName.equals(slackUser.getUsername()))
        .collect(Collectors.toList());
    usersDone.add(slackUser.getUsername());

    String updBlocks = malePollUpdatedBody(gameRoom.getId(), currentTask.getTitle(), estimationScale.getMarks(),
        usersDone);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", updBlocks);

    return ctx.ack();
  }

  private Response handlePollStop(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String gameRoomId = req.getPayload().getActions().get(0).getValue();

    mongoDao.saveUserIfNotExists(new User(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = mongoDao.getGameRoom(gameRoomId);
    Task currentTask = gameRoom.getNextTask().get();

    String blocks = makePollEndedBody(slackUser.getUsername(), currentTask.getEstimations());
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    return ctx.ack();
  }

  private Response handleTaskDone(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();
    String finalMark = req.getPayload().getActions().get(0).getValue();

    mongoDao.saveUserIfNotExists(new User(slackUser.getId(), slackUser.getName()));

    GameRoom gameRoom = mongoDao.getGameRoomByThreadId(threadId);
    Task currentTask = gameRoom.getNextTask().get();

    currentTask.setFinalMark(finalMark);
    mongoDao.save(gameRoom);

    String blocks = makeTaskEndedBody(slackUser.getUsername(), currentTask.getTitle(), currentTask.getEstimations(),
        finalMark);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    if (gameRoom.getNextTask().isPresent()) {
      postNextTaskButton(ctx.client(), gameRoom.getId(), channel.getId(), threadId);
    } else {
      String blocks2 = makeGameRoomEndedBody(gameRoom);
      ChatPostMessageResponse response = ctx.client().chatPostMessage(r -> r
          .channel(channel.getId())
          .threadTs(threadId)
          .text("результаты")
          .blocksAsString(blocks2));
      if (!response.isOk()) {
        System.out.println("chatPostMessage failed: " + response.getError());
      }
    }
    return ctx.ack();
  }

  private Response handleJiraButton(BlockActionRequest req, ActionContext ctx) throws IOException, SlackApiException {
    BlockActionPayload.Channel channel = req.getPayload().getChannel();
    String messageId = req.getPayload().getMessage().getTs();
    String threadId = req.getPayload().getMessage().getThreadTs();
    BlockActionPayload.User slackUser = req.getPayload().getUser();

    mongoDao.saveUserIfNotExists(new User(slackUser.getId(), slackUser.getName()));
    GameRoom gameRoom = mongoDao.getGameRoomByThreadId(threadId);

    User user = prepareLdapUser(slackUser.getId());
    createJiraIssues(gameRoom, user);

    String portfolioLink = "https://jira.hh.ru/browse/" + gameRoom.getPortfolioKey();
    String blocks = makeJiraDoneBody(gameRoom, slackUser.getUsername(), portfolioLink);
    updateSlackMessage(ctx.client(), channel.getId(), messageId, "голосование", blocks);

    return ctx.ack();
  }

  private Optional<String> extractPortfolioKey(String portfolioString) {
    Matcher matcher_long = PORTFOLIO_PATTERN_LONG.matcher(portfolioString);
    if (matcher_long.matches()) {
      return Optional.of(matcher_long.group(1));
    }

    Matcher matcher_short = PORTFOLIO_PATTERN_SHORT.matcher(portfolioString);
    if (matcher_short.matches()) {
      return Optional.of(matcher_short.group(1));
    }

    return Optional.empty();
  }

  private void postNextTaskButton(
      MethodsClient client,
      String gameRoomId,
      String channelId,
      String threadId
  ) throws SlackApiException, IOException {
    String body = makeNextTaskButtonBody(gameRoomId);
    ChatPostMessageResponse response = client.chatPostMessage(r -> r
        .channel(channelId)
        .threadTs(threadId)
        .text("начать оценку")
        .blocksAsString(body));
    if (!response.isOk()) {
      System.out.println("chatPostMessage failed: " + response.getError());
    }
  }

  private void updateSlackMessage(
      MethodsClient client,
      String channelId,
      String messageId,
      String title,
      String blocks
  ) throws SlackApiException, IOException {
    var updMessage = client.chatUpdate(r -> r
        .channel(channelId)
        .ts(messageId)
        .text(title)
        .blocksAsString(blocks)
    );
    if (!updMessage.isOk()) {
      System.out.println("chat.postMessage failed: " + updMessage.getError());
    }
  }

  public User prepareLdapUser(String userId) {
    User user = mongoDao.getUserBySlackId(userId);
    if (user.getLdapUserName() == null || user.getLdapTeamName() == null) {
      Pair<CrabTeam, CrabTeam.Mate> crabUser = getCrabUser(user.getSlackUserName());

      user.setLdapUserName(crabUser.getSecond().employee.login);
      if (!crabUser.getFirst().name.startsWith("Команда ")) {
        throw new RuntimeException("Team not found");
      }
      user.setLdapTeamName(crabUser.getFirst().name.substring(8));
      mongoDao.save(user);
    }

    return user;
  }

  public Pair<CrabTeam, CrabTeam.Mate> getCrabUser(String slackUserName) {
    List<CrabTeam> resp;
    try {
      resp = webClient.get()
          .uri(new URI("https://crab.pyn.ru/users"))
          .header("Content-Type", "application/json")
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<List<CrabTeam>>() {})
          .log()
          .block();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("123");
    }

    return resp.stream()
        .map(team -> team.activeMembers.stream().map(mate -> Pair.of(team, mate)))
        .flatMap(Function.identity())
        .filter(item -> ("@" + slackUserName).equals(item.getSecond().employee.slack))
        .findFirst()
        .orElseThrow();
  }

  private void createJiraIssues(GameRoom gameRoom, User user) {
    JiraIssuesCreated creationResponse;
    try {
      String issuesBody = makeJiraIssueBody(gameRoom, user);
      creationResponse = webClient.post()
          .uri(new URI("https://jira.hh.ru/rest/api/2/issue/bulk"))
          .header("Content-Type", "application/json")
          .header("Authorization", "Basic " + jiraToken)
          .body(BodyInserters.fromValue(issuesBody))
          .retrieve()
          .bodyToMono(JiraIssuesCreated.class)
          .log()
          .block();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("");
    }

    creationResponse.issues.forEach(issue -> {
      try {
        String issueLinkBody = makeJiraIssueLinkBody(issue.key, gameRoom.getPortfolioKey());
        webClient.post()
            .uri(new URI("https://jira.hh.ru/rest/api/2/issueLink"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + jiraToken)
            .body(BodyInserters.fromValue(issueLinkBody))
            .retrieve()
            .bodyToMono(JiraIssuesCreated.class)
            .log()
            .block();
      } catch (URISyntaxException e) {
        e.printStackTrace();
        throw new RuntimeException("123");
      }
    });
  }

// JIRA
//  так можно вытащить жира команды
//  curl -X GET 'https://jira.hh.ru/rest/api/2/issue/createmeta?projectKeys=HH&issuetypeNames=Task&expand=projects.issuetypes.fields' \
//      --header 'Accept: application/json' \
//      --header 'Authorization: Basic c123=' \
//
//
//  так можно поискать людей
//  curl -X GET 'https://jira.hh.ru/rest/api/2/user/search?username=r.kozlov' \
//      --header 'Accept: application/json' \
//      --header 'Authorization: Basic c1231=' \
//


  public Response emodzi(EventsApiPayload<ReactionAddedEvent> payload, EventContext ctx) {
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
    return ctx.ack();
  }


  public String makeJiraDoneBody(GameRoom gameRoom, String userName, String portfolioLink) {
    String roomResults = gameRoom.getTasks().stream()
        .map(t -> t.getTitle() + " - " + t.getFinalMark())
        .collect(Collectors.joining("\n"));

    return """
        [
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "Результаты:\\n%s",
              "emoji": true
            }
          },
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "(%s)\\n%s",
              "emoji": true
            }
          }
        ]
        """.formatted(roomResults, userName, portfolioLink);
  }

  public String makeGameRoomEndedBody(GameRoom gameRoom) {
    String roomResults = gameRoom.getTasks().stream()
        .map(t -> t.getTitle() + " - " + t.getFinalMark())
        .collect(Collectors.joining("\n"));

    return """
        [
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "Результаты:\\n%s",
              "emoji": true
            }
          },
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Запостить в жиру",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(roomResults, gameRoom.getId(), CREATE_JIRA_ISSUE_EVENT);
  }

  public String makeTaskEndedBody(
      String userName,
      String taskTitle,
      List<TaskEstimation> estimations,
      String finalMark
  ) {
    String pollResults = estimations.stream()
        .map(e -> e.getUserName() + " " + e.getMark())
        .collect(Collectors.joining("\n"));

    return """
        [{
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "(%s)\\n%s\\nРезультаты:\\n%s",
            "emoji": true
          }
        },
        {
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "Итоговая оценка: %s",
            "emoji": true
          }
        }]
        """.formatted(userName, taskTitle, pollResults, finalMark);
  }

  public String makePollEndedBody(String userName, List<TaskEstimation> estimations) {
    String pollResults = estimations.stream()
        .map(e -> e.getUserName() + " " + e.getMark())
        .collect(Collectors.joining("\n"));

    return """
        [{
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "%s завершает голосование",
            "emoji": true
          }
        },
        {
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "Результаты:\\n%s",
            "emoji": true
          }
        },
        {
          "dispatch_action": true,
          "type": "input",
          "element": {
            "type": "plain_text_input",
            "action_id": "%s"
          },
          "label": {
            "type": "plain_text",
            "text": "Итоговая оценка",
            "emoji": true
          }
        }]
        """.formatted(userName, pollResults, TASK_ESTIMATED_EVENT);
  }

  public String makeRoomCreationModalBody(List<EstimationScale> estimationScales) {
    String scaleOptions = estimationScales.stream()
        .map(item -> """
          {
            "label": "%s: %s",
            "value": "%s"
          },
          """.formatted(item.getName(), item.getMarks(), item.getId())
        )
        .collect(Collectors.joining(" "));
    return """
        {
            "callback_id": "%s",
            "title": "Создать комнату",
            "submit_label": "Создать",
            "elements": [
                {
                    "label": "Ссылка на портфель",
                    "type": "text",
                    "name": "portfolio_key"
                },
                {
                    "label": "Список задач",
                    "type": "textarea",
                    "name": "tasks_text",
                    "hint": "на отдельных строчках"
                },
                {
                    "label": "Выбрать шкалу",
                    "type": "select",
                    "name": "estimation_scale_id",
                    "options": [
        """.formatted(CREATE_ROOM_REQUEST_EVENT)
        + scaleOptions +
        """
                        {
                          "label": "Новая шкала",
                          "value": "new_scale"
                        }
                      ]
                },
                {
                    "label": "Своя шкала",
                    "type": "text",
                    "name": "new_estimation_scale",
                    "optional": true
                }
            ]
        }
        """;
  }


  public String makeNextTaskButtonBody(String gameRoomId) {
    return """
        [
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Следующая задача",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(gameRoomId, NEXT_TASK_EVENT);
  }

  public String makePollStartedBody(String userName, String gameRoomId, String taskTitle, List<String> marks) {
    String taskTitle1 = taskTitle;
    if (userName != null) {
      taskTitle1 = "(" + userName + ")\n" + taskTitle;
    }
    String taskTitleBlock = """
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            }
          },
      """.formatted(taskTitle1);

    String marksBlock = marks.stream()
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
          """.formatted(mark, mark, POLL_OPTION_SELECTED_EVENT, mark)
        )
        .collect(Collectors.joining(", "));

    return """
        [
        """
        + taskTitleBlock +
        """
          {
            "type": "actions",
            "elements": [
        """
        + marksBlock +
        """
            ]
          },
          {
            "type": "divider"
          },
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {
                  "type": "plain_text",
                  "text": "Закончить голосование",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(gameRoomId, POLL_ENDED_EVENT);
  }

  public String malePollUpdatedBody(String gameRoomId, String taskTitle, List<String> marks, List<String> users) {
    String usersDone = """
        {
          "type": "section",
          "text": {
            "type": "plain_text",
            "text": "%s",
            "emoji": true
          }
        },
    """.formatted("Нажали:\n" + String.join("\n", users));

    String taskTitleBlock = """
          {
            "type": "section",
            "text": {
              "type": "plain_text",
              "text": "%s",
              "emoji": true
            }
          },
      """.formatted(taskTitle);

    String marksBlock = marks.stream()
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
          """.formatted(mark, mark, POLL_OPTION_SELECTED_EVENT, mark)
        )
        .collect(Collectors.joining(", "));

    return """
        [
        """
        + taskTitleBlock +
        """
          {
            "type": "actions",
            "elements": [
        """
        + marksBlock +
        """
            ]
          },
          {
            "type": "divider"
          },
        """
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
                  "text": "Завершить голосование",
                  "emoji": true
                },
                "value": "%s",
                "action_id": "%s"
              }
            ]
          }
        ]
        """.formatted(gameRoomId, POLL_ENDED_EVENT);
  }

  private String makeJiraIssueBody(GameRoom gameRoom, User user) {
    String issues = gameRoom.getTasks().stream().map(task -> """
        {
            "fields": {
                "project": {
                    "key": "HH"
                },
                "summary": "%s",
                "issuetype": {
                    "name": "Task"
                },
                "assignee":{"name":"%s"},
                "customfield_10961": {"value": "%s"},
                "customfield_11212": %s
            }
        }
        """.formatted(task.getTitle(), user.getLdapUserName(), user.getLdapTeamName(), task.getStoryPoints())
    ).collect(Collectors.joining(", "));

    return """
            {
                "issueUpdates": [
                """
        + issues +
        """
                ]
            }
        """;
  }

  private String makeJiraIssueLinkBody(String issueKey, String portfolioKey) {
    return """
            {
                "type": {
                    "name": "Inclusion"
                },
                "inwardIssue": {
                    "key": "%s"
                },
                "outwardIssue": {
                    "key": "%s"
                }
            }
          """.formatted(portfolioKey, issueKey);
  }

}
