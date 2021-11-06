package com.example.darby.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class SlackChannel {

  @Id
  private String id;
  private String slackId;
  private String name;

  public SlackChannel(String slackId, String name) {
    this.slackId = slackId;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSlackId() {
    return slackId;
  }

  public void setSlackId(String slackId) {
    this.slackId = slackId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}