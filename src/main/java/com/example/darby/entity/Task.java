package com.example.darby.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class Task {

  @Id
  @Column("task_id")
  private Integer id;
  private Integer gameRoomId;
  private Integer taskOrder;
  private String title;
  private String finalMark;

  public Task() {
  }

  public Task(Integer gameRoomId, String title, Integer taskOrder) {
    this.gameRoomId = gameRoomId;
    this.title = title;
    this.taskOrder = taskOrder;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getGameRoomId() {
    return gameRoomId;
  }

  public void setGameRoomId(Integer gameRoomId) {
    this.gameRoomId = gameRoomId;
  }

  public Integer getTaskOrder() {
    return taskOrder;
  }

  public void setTaskOrder(Integer taskOrder) {
    this.taskOrder = taskOrder;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getFinalMark() {
    return finalMark;
  }

  public void setFinalMark(String finalMark) {
    this.finalMark = finalMark;
  }

  public String getStoryPoints() {
    if (getFinalMark().matches("[0-9]*\\.?[0-9]+")) {
      return getFinalMark();
    }
    return null;
  }
}