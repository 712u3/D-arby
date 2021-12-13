package com.example.darby.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class HistoryLog {

  @Id
  @Column("history_log_id")
  private Integer id;
  private Integer gameRoomId;
  private String slackUserId;
  private String message;
  private Instant created;

  public HistoryLog() {
  }

  public HistoryLog(Integer gameRoomId, String slackUserId, String message) {
    this.gameRoomId = gameRoomId;
    this.slackUserId = slackUserId;
    this.message = message;
    this.created = Instant.now();
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

  public String getSlackUserId() {
    return slackUserId;
  }

  public void setSlackUserId(String slackUserId) {
    this.slackUserId = slackUserId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }
}