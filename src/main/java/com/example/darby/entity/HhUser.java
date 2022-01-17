package com.example.darby.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

public class HhUser {

  @Id
  @Column("hhuser_id")
  private Integer id;
  private String slackUserId;
  private String slackUserName;
  private String ldapUserName;
  private String ldapTeamName;

  public HhUser() {
  }

  public HhUser(String slackUserId, String slackUserName) {
    this.slackUserId = slackUserId;
    this.slackUserName = slackUserName;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getSlackUserId() {
    return slackUserId;
  }

  public void setSlackUserId(String slackUserId) {
    this.slackUserId = slackUserId;
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