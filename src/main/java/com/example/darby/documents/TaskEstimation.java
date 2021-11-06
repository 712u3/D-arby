package com.example.darby.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class TaskEstimation {

  @Id
  private String id;
  private String taskId;
  private String userId;
  private String mark;

  public TaskEstimation(String id, String taskId, String userId, String mark) {
    this.id = id;
    this.taskId = taskId;
    this.userId = userId;
    this.mark = mark;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getMark() {
    return mark;
  }

  public void setMark(String mark) {
    this.mark = mark;
  }
}