package com.example.darby.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class GameRoom {

  @Id
  @Column("game_room_id")
  private Integer id;
  private String portfolioKey;
  private Integer estimationScaleId;
  private String slackUserId;
  private String channelId;
  private String threadId;
  private Instant created;

  public GameRoom() {
  }

  public GameRoom(String portfolioKey, Integer estimationScaleId, String slackUserId, String channelId,
                  String threadId) {
    this.portfolioKey = portfolioKey;
    this.estimationScaleId = estimationScaleId;
    this.slackUserId = slackUserId;
    this.channelId = channelId;
    this.threadId = threadId;
    this.created = Instant.now();
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getPortfolioKey() {
    return portfolioKey;
  }

  public void setPortfolioKey(String portfolioKey) {
    this.portfolioKey = portfolioKey;
  }

  public Integer getEstimationScaleId() {
    return estimationScaleId;
  }

  public void setEstimationScaleId(Integer estimationScaleId) {
    this.estimationScaleId = estimationScaleId;
  }

  public String getSlackUserId() {
    return slackUserId;
  }

  public void setSlackUserId(String slackUserId) {
    this.slackUserId = slackUserId;
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

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }
}