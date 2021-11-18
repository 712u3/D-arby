package com.example.darby.document;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class EstimationScale {

  @Id
  @Column("estimation_scale_id")
  private Integer id;
  private String name;
  private List<String> marks;
  private Boolean basic;

  public EstimationScale() {
  }

  public EstimationScale(String name, List<String> marks) {
    this.name = name;
    this.marks = marks;
    this.basic = false;
  }

  public EstimationScale(String name, List<String> marks, Boolean basic) {
    this(name, marks);
    this.basic = basic;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
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

  public Boolean getBasic() {
    return basic;
  }

  public void setBasic(Boolean basic) {
    this.basic = basic;
  }
}