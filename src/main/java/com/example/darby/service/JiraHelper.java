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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

  public Mono<Void> createJiraIssues(String portfolioKey, List<Task> tasks, HhUser user) {
    Mono<JiraIssuesCreated> createJiraIssuesMono = webClient.post()
        .uri(createUri("https://jira.hh.ru/rest/api/2/issue/bulk"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Basic " + jiraToken)
        .body(BodyInserters.fromValue(makeJiraIssueBody(tasks, user)))
        .retrieve()
        .bodyToMono(JiraIssuesCreated.class)
        .log();


    return createJiraIssuesMono.flatMap(jiraIssuesCreated ->
        createJiraIssuesLinkMono(jiraIssuesCreated, portfolioKey));
  }

  private Mono<Void> createJiraIssuesLinkMono(JiraIssuesCreated jiraIssuesCreated, String portfolioKey) {
    List<Mono<JiraIssuesCreated>> monoList = jiraIssuesCreated.issues.stream()
        .map(issue -> makeJiraIssueLinkMono(issue.key, portfolioKey))
        .collect(Collectors.toList());

    return Flux.zip(monoList, objects -> objects).then();
  }

  private Mono<JiraIssuesCreated> makeJiraIssueLinkMono(String issueKey, String portfolioKey) {
    return webClient.post()
        .uri(createUri("https://jira.hh.ru/rest/api/2/issueLink"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Basic " + jiraToken)
        .body(BodyInserters.fromValue(makeJiraIssueLinkBody(issueKey, portfolioKey)))
        .retrieve()
        .bodyToMono(JiraIssuesCreated.class)
        .log();
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

    if ("stub".equals(portfolioString)) {
      return Optional.of(portfolioString);
    }

    return Optional.empty();
  }

  public Optional<Float> makeSumStoryPoints(List<Task> tasks) {
    List<Float> floatMarks = tasks.stream()
        .map(Task::getFinalMark)
        .filter(JiraHelper::isFloat)
        .map(Float::parseFloat)
        .collect(Collectors.toList());

    if (tasks.size() != floatMarks.size()) {
      return Optional.empty();
    }
    return Optional.of(floatMarks.stream().reduce(0.0f, Float::sum));
  }

  static boolean isInt(String s) {
    try {
      int i = Integer.parseInt(s);
      return true;
    } catch(NumberFormatException er) {
      return false;
    }
  }

  static boolean isFloat(String s) {
    try {
      float f = Float.parseFloat(s);
      return true;
    } catch(NumberFormatException er) {
      return false;
    }
  }

  static URI createUri(String url) {
    URI issuesUri;
    try {
      issuesUri = new URI(url);
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("impossible, uri is not done");
    }

    return issuesUri;
  }
}
