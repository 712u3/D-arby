package com.example.darby.documents;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class GameRoom {

  @Id
  private String id;
  private String title;
  private String estimationScaleId;
  private String channelId;
  private String threadId;
  private List<Task> tasks;

  public GameRoom(String title, String estimationScaleId, String channelId, String threadId, List<Task> tasks) {
    this.title = title;
    this.estimationScaleId = estimationScaleId;
    this.channelId = channelId;
    this.threadId = threadId;
    this.tasks = tasks;
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

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public String getThreadId() {
    return threadId;
  }

  public void setThreadId(String threadId) {
    this.threadId = threadId;
  }

  public List<Task> getTasks() {
    return tasks;
  }

  public void setTasks(List<Task> tasks) {
    this.tasks = tasks;
  }
}