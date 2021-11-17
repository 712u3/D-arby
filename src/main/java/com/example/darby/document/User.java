package com.example.darby.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class User {

  @Id
  private String id;
  private String slackId;
  private String slackUserName;
  private String ldapUserName;
  private String ldapTeamName;

  public User(String slackId, String slackUserName) {
    this.slackId = slackId;
    this.slackUserName = slackUserName;
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

  public String getSlackUserName() {
    return slackUserName;
  }

  public void setSlackUserName(String slackUserName) {
    this.slackUserName = slackUserName;
  }

  public String getLdapUserName() {
    return ldapUserName;
  }

  public void setLdapUserName(String ldapUserName) {
    this.ldapUserName = ldapUserName;
  }

  public String getLdapTeamName() {
    return ldapTeamName;
  }

  public void setLdapTeamName(String ldapTeamName) {
    this.ldapTeamName = ldapTeamName;
  }
}