package com.example.darby.dao;

import com.example.darby.documents.EstimationScale;
import com.example.darby.documents.RoomLocation;
import com.example.darby.documents.SlackChannel;
import com.example.darby.documents.SlackUser;
import com.mongodb.client.result.UpdateResult;
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
    EstimationScale result1 = reactiveMongoTemplate.find(findQuery, EstimationScale.class).blockFirst();
    if (result1 != null) {
      return result1.getId();
    }

    Query upsertQuery = new Query();
    upsertQuery.addCriteria(criteria);

    Update update = new Update();
    update.set("marks", estimationScale.getMarks());

    UpdateResult result2 = reactiveMongoTemplate.upsert(upsertQuery, update, EstimationScale.class).block();
    if (result2.getMatchedCount() > 0) {
      return reactiveMongoTemplate.find(findQuery, EstimationScale.class).blockFirst().getId();
    }
    return result2.getUpsertedId().asObjectId().getValue().toHexString();
  }

  public String upsert(SlackUser slackUser) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("slack_id").is(slackUser.getSlackId());
    findQuery.addCriteria(criteria);
    SlackUser result1 = reactiveMongoTemplate.find(findQuery, SlackUser.class).blockFirst();
    if (result1 != null) {
      return result1.getId();
    }

    Query upsertQuery = new Query();
    upsertQuery.addCriteria(criteria);

    Update update = new Update();
    update.set("slack_id", slackUser.getSlackId());
    update.set("name", slackUser.getName());

    UpdateResult result2 = reactiveMongoTemplate.upsert(upsertQuery, update, SlackUser.class).block();
    if (result2.getMatchedCount() > 0) {
      return reactiveMongoTemplate.find(findQuery, SlackUser.class).blockFirst().getId();
    }
    return result2.getUpsertedId().asObjectId().getValue().toHexString();
  }

  public String upsert(SlackChannel slackChannel) {
    Query findQuery = new Query();
    Criteria criteria = Criteria.where("slack_id").is(slackChannel.getSlackId());
    findQuery.addCriteria(criteria);
    SlackChannel result1 = reactiveMongoTemplate.find(findQuery, SlackChannel.class).blockFirst();
    if (result1 != null) {
      return result1.getId();
    }

    Query upsertQuery = new Query();
    upsertQuery.addCriteria(criteria);

    Update update = new Update();
    update.set("slack_id", slackChannel.getSlackId());
    update.set("name", slackChannel.getName());

    UpdateResult result2 = reactiveMongoTemplate.upsert(upsertQuery, update, SlackChannel.class).block();
    if (result2.getMatchedCount() > 0) {
      return reactiveMongoTemplate.find(findQuery, SlackChannel.class).blockFirst().getId();
    }
    return result2.getUpsertedId().asObjectId().getValue().toHexString();
  }

  public <T> T save(T entity) {
    return null;
  }
}
