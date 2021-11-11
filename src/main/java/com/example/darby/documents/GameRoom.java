package com.example.darby.documents;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class GameRoom {

  @Id
  private String id;
  private String portfolioKey;
  private String estimationScaleId;
  private String userId;
  private String channelId;
  private String threadId;
  private List<Task> tasks;
  private Instant created;

  public GameRoom(String portfolioKey, String estimationScaleId, String userId, String channelId,
                  String threadId, List<Task> tasks) {
    this.portfolioKey = portfolioKey;
    this.estimationScaleId = estimationScaleId;
    this.userId = userId;
    this.channelId = channelId;
    this.threadId = threadId;
    this.tasks = tasks;
    this.created = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPortfolioKey() {
    return portfolioKey;
  }

  public void setPortfolioKey(String portfolioKey) {
    this.portfolioKey = portfolioKey;
  }

  public String getEstimationScaleId() {
    return estimationScaleId;
  }

  public void setEstimationScaleId(String estimationScaleId) {
    this.estimationScaleId = estimationScaleId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
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

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }

  public Optional<Task> getNextTask() {
    return getTasks().stream()
        .filter(task -> task.getFinalMark() == null)
        .findFirst();
  }
}