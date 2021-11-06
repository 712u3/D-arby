package com.example.darby.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class GameRoom {

  @Id
  private String id;
  private String title;
  private String estimationScaleId;
  private String roomLocationId;

  public GameRoom(String title, String estimationScaleId, String roomLocationId) {
    this.title = title;
    this.estimationScaleId = estimationScaleId;
    this.roomLocationId = roomLocationId;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getEstimationScaleId() {
    return estimationScaleId;
  }

  public void setEstimationScaleId(String estimationScaleId) {
    this.estimationScaleId = estimationScaleId;
  }

  public String getRoomLocationId() {
    return roomLocationId;
  }

  public void setRoomLocationId(String roomLocationId) {
    this.roomLocationId = roomLocationId;
  }
}