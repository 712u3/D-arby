package com.example.darby.dao;

import com.example.darby.documents.EstimationScale;
import com.example.darby.documents.GameRoom;
import com.example.darby.documents.Task;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDao {
  private final ReactiveMongoTemplate reactiveMongoTemplate;

  public MongoDao(ReactiveMongoTemplate reactiveMongoTemplate) {
    this.reactiveMongoTemplate = reactiveMongoTemplate;
  }

  public String upsert(EstimationScale estimationScale) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("marks").is(estimationScale.getMarks());
    findQuery.addCriteria(criteria);

    reactiveMongoTemplate.createCollection(EstimationScale.TABLE_NAME).block();

    var estimations = reactiveMongoTemplate.findAll(EstimationScale.class, EstimationScale.TABLE_NAME)
        .collectList().block();

    if (estimations != null) {
      return estimations.get(0).getId();
    }
    // race condition
    // https://github.com/spring-projects/spring-data-mongodb/issues/2452
    return reactiveMongoTemplate.save(estimationScale, EstimationScale.TABLE_NAME).block().getId();
  }


  public <T> T save(T entity) {
    return reactiveMongoTemplate.save(entity).block();
  }

  public void findTaskByThreadId() {
//    // походу как даун  сначала залесекчу румлокатион
//    // потом рум
//    // потом таску
//    String threadId = "";
//    Query roomLocQuery = new Query();
//    Criteria criteria1 = Criteria.where("thread_id").is(threadId);
//    roomLocQuery.addCriteria(criteria1);
//    RoomLocation roomLocation = reactiveMongoTemplate.find(roomLocQuery, RoomLocation.class).blockFirst();
//
//
//    Query roomQuery = new Query();
//    Criteria criteria2 = Criteria.where("room_location_id").is(roomLocation.getId());
//    roomQuery.addCriteria(criteria2);
//    GameRoom gameRoom = reactiveMongoTemplate.find(roomQuery, GameRoom.class).blockFirst();
//
//
//    Query taskQuery = new Query();
//    // нужны колонки время старта/завершения  чтобы селектили те которые еще не завершены
//    Criteria criteria3 = Criteria.where("game_room_id").is(gameRoom.getId());
//    taskQuery.addCriteria(criteria3);
//    Task task = reactiveMongoTemplate.find(taskQuery, Task.class).blockFirst();


  }
}
