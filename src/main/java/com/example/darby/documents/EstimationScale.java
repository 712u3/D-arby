package com.example.darby.documents;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class EstimationScale {

  @Id
  private String id;
  private String name;
  private List<String> marks;
  private Boolean primary;

  public EstimationScale(String name, List<String> marks) {
    this.name = name;
    this.marks = marks;
    this.primary = false;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getMarks() {
    return marks;
  }

  public void setMarks(List<String> marks) {
    this.marks = marks;
  }

  public Boolean getPrimary() {
    return primary;
  }

  public void setPrimary(Boolean primary) {
    this.primary = primary;
  }
}