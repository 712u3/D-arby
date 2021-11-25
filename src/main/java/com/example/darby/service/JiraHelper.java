package com.example.darby.service;

import com.example.darby.dto.JiraIssuesCreated;
import com.example.darby.entity.HhUser;
import com.example.darby.entity.Task;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class JiraHelper {
  public final Pattern PORTFOLIO_PATTERN_LONG = Pattern.compile("^https://jira\\.hh\\.ru/browse/(PORTFOLIO-\\d+)$");
  public final Pattern PORTFOLIO_PATTERN_SHORT = Pattern.compile("^(PORTFOLIO-\\d+)$");

  private final String jiraToken;
  private final WebClient webClient;

  public JiraHelper(@Value("${jira-username}") String jiraUsername,
                    @Value("${jira-password}") String jiraPassword,
                    WebClient webClient) {
    String jiraCred = jiraUsername + ":" + jiraPassword;
    this.jiraToken = Base64.getEncoder().encodeToString(jiraCred.getBytes());
    this.webClient = webClient;
  }

  public void createJiraIssues(String portfolioKey, List<Task> tasks, HhUser user) {
    JiraIssuesCreated creationResponse;
    try {
      String issuesBody = makeJiraIssueBody(tasks, user);
      creationResponse = webClient.post()
          .uri(new URI("https://jira.hh.ru/rest/api/2/issue/bulk"))
          .header("Content-Type", "application/json")
          .header("Authorization", "Basic " + jiraToken)
          .body(BodyInserters.fromValue(issuesBody))
          .retrieve()
          .bodyToMono(JiraIssuesCreated.class)
          .log()
          .block();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("");
    }

    creationResponse.issues.forEach(issue -> {
      try {
        String issueLinkBody = makeJiraIssueLinkBody(issue.key, portfolioKey);
        webClient.post()
            .uri(new URI("https://jira.hh.ru/rest/api/2/issueLink"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + jiraToken)
            .body(BodyInserters.fromValue(issueLinkBody))
            .retrieve()
            .bodyToMono(JiraIssuesCreated.class)
            .log()
            .block();
      } catch (URISyntaxException e) {
        e.printStackTrace();
        throw new RuntimeException("123");
      }
    });
  }

  private String makeJiraIssueBody(List<Task> tasks, HhUser user) {
    String issues = tasks.stream().map(task -> """
        {
            "fields": {
                "project": {
                    "key": "HH"
                },
                "summary": "%s",
                "issuetype": {
                    "name": "Task"
                },
                "assignee":{"name":"%s"},
                "customfield_10961": {"value": "%s"},
                "customfield_11212": %s
            }
        }
        """.formatted(task.getTitle(), user.getLdapUserName(), user.getLdapTeamName(), task.getStoryPoints())
    ).collect(Collectors.joining(", "));

    return """
            {
                "issueUpdates": [
                """
        + issues +
        """
                ]
            }
        """;
  }

  private String makeJiraIssueLinkBody(String issueKey, String portfolioKey) {
    return """
            {
                "type": {
                    "name": "Inclusion"
                },
                "inwardIssue": {
                    "key": "%s"
                },
                "outwardIssue": {
                    "key": "%s"
                }
            }
          """.formatted(portfolioKey, issueKey);
  }

// JIRA
//  так можно вытащить жира команды
//  curl -X GET 'https://jira.hh.ru/rest/api/2/issue/createmeta?projectKeys=HH&issuetypeNames=Task&expand=projects.issuetypes.fields' \
//      --header 'Accept: application/json' \
//      --header 'Authorization: Basic c123=' \
//
//
//  так можно поискать людей
//  curl -X GET 'https://jira.hh.ru/rest/api/2/user/search?username=v.pupkin' \
//      --header 'Accept: application/json' \
//      --header 'Authorization: Basic c1231=' \
//

  public Optional<String> extractPortfolioKey(String portfolioString) {
    Matcher matcher_long = PORTFOLIO_PATTERN_LONG.matcher(portfolioString);
    if (matcher_long.matches()) {
      return Optional.of(matcher_long.group(1));
    }

    Matcher matcher_short = PORTFOLIO_PATTERN_SHORT.matcher(portfolioString);
    if (matcher_short.matches()) {
      return Optional.of(matcher_short.group(1));
    }

    return Optional.empty();
  }

  public Optional<Integer> makeSumStoryPoints(List<Task> tasks) {
    List<Integer> intMarks = tasks.stream()
        .map(Task::getFinalMark)
        .filter(JiraHelper::isInt)
        .map(Integer::parseInt)
        .collect(Collectors.toList());

    if (tasks.size() != intMarks.size()) {
      return Optional.empty();
    }
    return Optional.of(intMarks.stream().reduce(0, Integer::sum));
  }

  static boolean isInt(String s) {
    try {
      int i = Integer.parseInt(s);
      return true;
    } catch(NumberFormatException er) {
      return false;
    }
  }

}
