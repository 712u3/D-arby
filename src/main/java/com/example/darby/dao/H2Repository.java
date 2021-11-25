package com.example.darby.dao;

import com.example.darby.entity.BasicEntity;
import com.example.darby.entity.EstimationScale;
import com.example.darby.entity.GameRoom;
import com.example.darby.entity.HhUser;
import com.example.darby.entity.Task;
import com.example.darby.entity.TaskEstimation;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface H2Repository extends ReactiveCrudRepository<BasicEntity, String> {

  @Query(
    """
    select *
    from game_room
    where slack_user_id = :slackUserId
    order by created desc
    limit 1
    """
  )
  Mono<EstimationScale> getLastRoomBySlackUserId(String slackUserId);

  @Query(
    """
    select *
    from estimation_scale
    where basic = true
      or estimation_scale_id in (:estimationScaleId)
    """
  )
  Flux<EstimationScale> getAllEstimationScales2(Integer estimationScaleId);

  @Query(
      """
      select *
      from hhuser
      where slack_id = :slackId
      """
  )
  Flux<HhUser> getUserBySlackId(String slackId);

  @Query(
      """
      select *
      from game_room
      where game_room_id = :gameRoomId
      """
  )
  Mono<GameRoom> getGameRoomById(Integer gameRoomId);

  @Query(
      """
      select *
      from estimation_scale
      where estimation_scale_id = :estimationScaleId
      """
  )
  Mono<EstimationScale> getEstimationScaleById(Integer estimationScaleId);

  @Query(
      """
      select *
      from task
      where game_room_id = :gameRoomId
        and final_mark is null
        and deleted = false
      order by task_order
      limit 1
      """
  )
  Mono<Task> getLastTaskByGameRoomId(Integer gameRoomId);

  @Query(
      """
      select *
      from game_room
      where thread_id = :threadId
      """
  )
  Flux<GameRoom> getGameRoomByThreadId(String threadId);

  @Query(
      """
      select *
      from task_estimation
      where task_id = :taskId
        and slack_user_name = :slackUserName
      """
  )
  Mono<TaskEstimation> getTaskEstimationByTaskIdAndUserName(Integer taskId, String slackUserName);

  @Query(
      """
      select *
      from task_estimation
      where task_id = :taskId
      """
  )
  Flux<TaskEstimation> getTaskEstimationsByTaskId(Integer taskId);

  @Query(
      """
      select *
      from task
      where game_room_id = :roomId
        and deleted = false
      """
  )
  Flux<Task> getRoomTasksByRoomId(Integer roomId);

  @Query(
      """
      select *
      from task
      where message_id = :messageId
      """
  )
  Mono<Task> getTaskByMessageId(String messageId);
}
