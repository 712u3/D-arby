package com.example.darby.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class TaskEstimation {

  @Id
  @Column("task_estimation_id")
  private Integer id;
  private Integer taskId;
  private String slackUserName;
  private String mark;

  public TaskEstimation() {
  }

  public TaskEstimation(Integer taskId, String slackUserName, String mark) {
    this.taskId = taskId;
    this.slackUserName = slackUserName;
    this.mark = mark;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getTaskId() {
    return taskId;
  }

  public void setTaskId(Integer taskId) {
    this.taskId = taskId;
  }

  public String getSlackUserName() {
    return slackUserName;
  }

  public void setSlackUserName(String slackUserName) {
    this.slackUserName = slackUserName;
  }

  public String getMark() {
    return mark;
  }

  public void setMark(String mark) {
    this.mark = mark;
  }
}