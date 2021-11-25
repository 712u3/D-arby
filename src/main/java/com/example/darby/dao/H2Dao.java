package com.example.darby.dao;

import com.example.darby.entity.EstimationScale;
import com.example.darby.entity.GameRoom;
import com.example.darby.entity.HhUser;
import com.example.darby.entity.Task;
import com.example.darby.entity.TaskEstimation;
import java.util.List;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class H2Dao {

  private final R2dbcEntityTemplate r2dbcTemplate;
  private final H2Repository h2Repository;

  public H2Dao(R2dbcEntityTemplate r2dbcTemplate, H2Repository h2Repository) {
    this.r2dbcTemplate = r2dbcTemplate;
    this.h2Repository = h2Repository;
  }

  public void prepareDatabase() {
    r2dbcTemplate.getDatabaseClient().sql("""
          DROP TABLE IF EXISTS task, task_estimation, hhuser, estimation_scale, game_room
        """)
        .fetch().rowsUpdated().block();

    r2dbcTemplate.getDatabaseClient().sql("""
          CREATE TABLE IF not EXISTS estimation_scale(
            estimation_scale_id integer auto_increment PRIMARY KEY,
            name VARCHAR(20),
            marks array,
            basic boolean
          )
        """)
        .fetch()
        .rowsUpdated()
        .block();

    r2dbcTemplate.getDatabaseClient().sql("""
          CREATE TABLE IF not EXISTS game_room(
            game_room_id integer auto_increment PRIMARY KEY,
            portfolio_key VARCHAR(30),
            estimation_scale_id integer,
            slack_user_id VARCHAR(30),
            channel_id VARCHAR(30),
            thread_id VARCHAR(30),
            created TIMESTAMP WITH TIME ZONE,
            ended boolean
          )
        """)
        .fetch()
        .rowsUpdated()
        .block();

    r2dbcTemplate.getDatabaseClient().sql("""
          CREATE TABLE IF not EXISTS hhuser(
            hhuser_id integer auto_increment PRIMARY KEY,
            slack_id VARCHAR(30),
            slack_user_name VARCHAR(30),
            ldap_user_name VARCHAR(30),
            ldap_team_name VARCHAR(30)
          )
        """)
        .fetch()
        .rowsUpdated()
        .block();

    r2dbcTemplate.getDatabaseClient().sql("""
          CREATE TABLE IF not EXISTS task(
            task_id integer auto_increment PRIMARY KEY,
            game_room_id integer,
            task_order integer,
            title VARCHAR(63),
            final_mark VARCHAR(30),
            message_id VARCHAR(30),
            stopped boolean,
            deleted boolean
          )
        """)
        .fetch()
        .rowsUpdated()
        .block();

    r2dbcTemplate.getDatabaseClient().sql("""
          CREATE TABLE IF not EXISTS task_estimation(
            task_estimation_id integer auto_increment PRIMARY KEY,
            task_id integer,
            slack_user_name VARCHAR(30),
            mark VARCHAR(30)
          )
        """)
        .fetch()
        .rowsUpdated()
        .block();

    r2dbcTemplate.insert(EstimationScale.class)
        .into("estimation_scale")
        .using(new EstimationScale("Scrum", List.of("0", "0.5", "1",
            "2", "3", "5", "8", "13", "20", "40", "100", "?", "coffee"), true))
        .block();

    r2dbcTemplate.insert(EstimationScale.class)
        .into("estimation_scale")
        .using(new EstimationScale("Фибоначи", List.of("0", "1", "2",
            "3", "5", "8", "13", "21", "34", "55", "89", "?", "coffee"), true))
        .block();

    r2dbcTemplate.insert(EstimationScale.class)
        .into("estimation_scale")
        .using(new EstimationScale("Последовательная", List.of("0", "1", "2",
            "3", "4", "5", "6", "7", "8", "9", "10", "?", "coffee"), true))
        .block();

    r2dbcTemplate.insert(EstimationScale.class)
        .into("estimation_scale")
        .using(new EstimationScale("Reel pocker", List.of("Ace", "2", "3",
            "5", "8", "King", "?", "coffee"), true))
        .block();

    r2dbcTemplate.insert(EstimationScale.class)
        .into("estimation_scale")
        .using(new EstimationScale("Майки", List.of("XS", "S", "M",
            "L", "XL", "XXL", "?", "coffee"), true))
        .block();
  }

  public List<EstimationScale> getAllEstimationScales(String slackUserId) {
    Integer estimationScaleId = h2Repository.getLastRoomBySlackUserId(slackUserId).blockOptional()
        .map(EstimationScale::getId).orElse(null);
    return h2Repository.getAllEstimationScales2(estimationScaleId).collectList().block();
  }

  // race condition
  public void saveUserIfNotExists(HhUser user) {
    HhUser existingUser = h2Repository.getUserBySlackId(user.getSlackId()).blockFirst();
    if (existingUser == null) {
      r2dbcTemplate.insert(HhUser.class).into("hhuser").using(user).block();
    }
  }

  public Integer saveEstimationScaleId(EstimationScale estimationScale) {
    return r2dbcTemplate.insert(EstimationScale.class).into("estimation_scale").using(estimationScale).block().getId();
  }

  public Integer saveGameRoom(GameRoom gameRoom) {
    return r2dbcTemplate.insert(GameRoom.class).into("game_room").using(gameRoom).block().getId();
  }

  public void saveTasks(List<Task> tasks) {
    Flux.fromStream(tasks.stream())
        .flatMap(task -> r2dbcTemplate.insert(Task.class).into("task").using(task))
        .blockLast();
  }

  public GameRoom getGameRoom(Integer gameRoomId) {
    return h2Repository.getGameRoomById(gameRoomId).block();
  }

  public EstimationScale getEstimationScale(Integer estimationScaleId) {
    return h2Repository.getEstimationScaleById(estimationScaleId).block();
  }

  public Task getCurrentTask(Integer gameRoomId) {
    return h2Repository.getLastTaskByGameRoomId(gameRoomId).block();
  }

  public GameRoom getGameRoomByThreadId(String threadId) {
    return h2Repository.getGameRoomByThreadId(threadId).blockFirst();
  }

  // race condition
  public void updateOrSaveTaskEstimation(Integer taskId, String slackUserName, String mark) {
    TaskEstimation estimation = h2Repository.getTaskEstimationByTaskIdAndUserName(taskId, slackUserName).block();
    if (estimation == null) {
      TaskEstimation taskEstimation = new TaskEstimation(taskId, slackUserName, mark);
      r2dbcTemplate.insert(TaskEstimation.class).into("task_estimation").using(taskEstimation).block();
    } else {
      Criteria criteria = Criteria.where("task_estimation_id").is(estimation.getId());
      Query query = Query.query(criteria);

      Update update = Update.update("mark", mark);
      r2dbcTemplate.update(TaskEstimation.class).inTable("task_estimation").matching(query).apply(update).block();
    }
  }

  public List<TaskEstimation> getTaskEstimations(Integer taskId) {
    return h2Repository.getTaskEstimationsByTaskId(taskId).collectList().block();
  }

  public void updateTaskField(Integer taskId, String field, Object value) {
    Criteria criteria = Criteria.where("task_id").is(taskId);
    Query query = Query.query(criteria);

    Update update = Update.update(field, value);
    r2dbcTemplate.update(Task.class).inTable("task").matching(query).apply(update).block();
  }

  public HhUser getUserBySlackId(String slackUserId) {
    return h2Repository.getUserBySlackId(slackUserId).blockFirst();
  }

  public void updateUser(HhUser user) {
    Criteria criteria = Criteria.where("hhuser_id").is(user.getId());
    Query query = Query.query(criteria);

    Update update = Update.update("ldap_user_name", user.getLdapUserName())
        .set("ldap_team_name", user.getLdapTeamName());
    r2dbcTemplate.update(HhUser.class).inTable("hhuser").matching(query).apply(update).block();
  }

  public List<Task> getGameRoomTasks(Integer roomId) {
    return h2Repository.getRoomTasksByRoomId(roomId).collectList().block();
  }

  public Task getTaskByMessageId(String messageId) {
    return h2Repository.getTaskByMessageId(messageId).block();
  }

  public void updateGameRoomField(Integer gameRoomId, String field, Object value) {
    Criteria criteria = Criteria.where("game_room_id").is(gameRoomId);
    Query query = Query.query(criteria);

    Update update = Update.update(field, value);
    r2dbcTemplate.update(GameRoom.class).inTable("game_room").matching(query).apply(update).block();
  }
}
