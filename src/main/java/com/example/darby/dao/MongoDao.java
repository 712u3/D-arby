package com.example.darby.dao;

import com.example.darby.documents.RoomLocation;
import com.example.darby.documents.SlackChannel;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDao {
  private final ReactiveMongoTemplate reactiveMongoTemplate;

  public MongoDao(ReactiveMongoTemplate reactiveMongoTemplate) {
    this.reactiveMongoTemplate = reactiveMongoTemplate;
  }

  public <T> T upsert(T entity) {
    return null;
  }

  public <T> T save(T entity) {
    return null;
  }
}
