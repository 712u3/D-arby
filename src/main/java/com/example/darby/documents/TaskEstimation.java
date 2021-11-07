package com.example.darby.documents;

public class TaskEstimation {

  private String userName;
  private String mark;

  public TaskEstimation(String userName, String mark) {
    this.userName = userName;
    this.mark = mark;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getMark() {
    return mark;
  }

  public void setMark(String mark) {
    this.mark = mark;
  }
}