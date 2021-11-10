package com.example.darby.documents;


import java.util.List;

public class Task {

  private String title;
  private List<TaskEstimation> estimations;
  private String finalMark;

  public Task(String title, List<TaskEstimation> estimations) {
    this.title = title;
    this.estimations = estimations;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<TaskEstimation> getEstimations() {
    return estimations;
  }

  public void setEstimations(List<TaskEstimation> estimations) {
    this.estimations = estimations;
  }

  public String getFinalMark() {
    return finalMark;
  }

  public void setFinalMark(String finalMark) {
    this.finalMark = finalMark;
  }
}