package com.example.darby.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Task {

  @Id
  private String id;
  private String gameRoomId;
  private String title;
  private Integer order;

  public Task(String gameRoomId, String title, Integer order) {
    this.gameRoomId = gameRoomId;
    this.title = title;
    this.order = order;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getGameRoomId() {
    return gameRoomId;
  }

  public void setGameRoomId(String gameRoomId) {
    this.gameRoomId = gameRoomId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }
}