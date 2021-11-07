package com.example.darby.documents;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class EstimationScale {
  public static final String TABLE_NAME = "estimation_scale";

  @Id
  private String id;
  private List<String> marks;

  public EstimationScale(List<String> marks) {
    this.marks = marks;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<String> getMarks() {
    return marks;
  }

  public void setMarks(List<String> marks) {
    this.marks = marks;
  }
}