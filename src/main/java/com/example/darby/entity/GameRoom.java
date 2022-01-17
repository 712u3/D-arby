package com.example.darby.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class GameRoom {

  @Id
  @Column("game_room_id")
  private Integer id;
  private String portfolioKey;
  private Integer estimationScaleId;
  private String slackUserId; // owner
  private String slackChannelId;
  private String slackThreadId; // main message
  private Instant created;
  private Boolean ended;

  public GameRoom() {
  }

  public GameRoom(String portfolioKey, Integer estimationScaleId, String slackUserId, String slackChannelId) {
    this.portfolioKey = portfolioKey;
    this.estimationScaleId = estimationScaleId;
    this.slackUserId = slackUserId;
    this.slackChannelId = slackChannelId;
    this.created = Instant.now();
    this.ended = false;
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

  public String getSlackChannelId() {
    return slackChannelId;
  }

  public void setSlackChannelId(String slackChannelId) {
    this.slackChannelId = slackChannelId;
  }

  public String getSlackThreadId() {
    return slackThreadId;
  }

  public void setSlackThreadId(String slackThreadId) {
    this.slackThreadId = slackThreadId;
  }

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }

  public Boolean getEnded() {
    return ended;
  }

  public void setEnded(Boolean ended) {
    this.ended = ended;
  }
}