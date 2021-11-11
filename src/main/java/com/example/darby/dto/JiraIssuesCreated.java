package com.example.darby.dto;

import java.util.List;

public class JiraIssuesCreated {
  public List<JiraIssueCreated> issues;

  public static class JiraIssueCreated {
    public String id;
    public String key;
    public String self;
  }
}
