package com.example.darby.dao;

import com.example.darby.documents.EstimationScale;
import com.example.darby.documents.GameRoom;
import com.example.darby.documents.Task;
import com.example.darby.documents.TaskEstimation;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  public List<EstimationScale> getAllEstimationScales(String userId) {
    Query findQuery1 = new Query();
    Criteria criteria1 = Criteria.where("userId").is(userId);
    findQuery1.addCriteria(criteria1);

    var lastGameRoom = reactiveMongoTemplate.find(findQuery1, GameRoom.class).blockLast();
    String lastEstimationScaleId = null;
    if (lastGameRoom != null) {
      lastEstimationScaleId = lastGameRoom.getEstimationScaleId();
    }

    Query findQuery = new Query();
    Criteria criteria = new Criteria().orOperator(
        Criteria.where("primary").is(true),
        Criteria.where("id").is(lastEstimationScaleId)
    );
    findQuery.addCriteria(criteria);

    return reactiveMongoTemplate.find(findQuery, EstimationScale.class).collectList().block();
  }

  public String getOrSave(EstimationScale estimationScale) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("marks").is(estimationScale.getMarks());
    findQuery.addCriteria(criteria);

    var estimationsCale = reactiveMongoTemplate.find(findQuery, EstimationScale.class).blockFirst();

    if (estimationsCale != null) {
      return estimationsCale.getId();
    }
    // race condition
    return reactiveMongoTemplate.save(estimationScale).block().getId();
  }


  public <T> T save(T entity) {
    return reactiveMongoTemplate.save(entity).block();
  }

  public GameRoom getGameRoom(String gameRoomId) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("id").is(gameRoomId);
    findQuery.addCriteria(criteria);

    return reactiveMongoTemplate.find(findQuery, GameRoom.class).blockFirst();
  }

  public GameRoom getGameRoomByThreadId(String threadId) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("threadId").is(threadId);
    findQuery.addCriteria(criteria);

    return reactiveMongoTemplate.find(findQuery, GameRoom.class).blockFirst();
  }

  public EstimationScale getEstimationScale(String estimationScaleId) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("id").is(estimationScaleId);
    findQuery.addCriteria(criteria);

    return reactiveMongoTemplate.find(findQuery, EstimationScale.class).blockFirst();
  }
}
