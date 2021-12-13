package com.example.darby.service;

import com.example.darby.dao.H2Dao;
import com.example.darby.dto.CrabTeam;
import com.example.darby.entity.HhUser;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class CrabHelper {

  private final H2Dao dao;
  private final WebClient webClient;

  public CrabHelper(H2Dao dao, WebClient webClient) {
    this.dao = dao;
    this.webClient = webClient;
  }

  public Mono<HhUser> getHhUserUserEnriched(String slackUserId, String slackUserName) {
    HhUser user = dao.getUserBySlackId(slackUserId);
    if (user == null) {
      user = new HhUser(slackUserId, slackUserName);
      dao.saveUser(user);
    }
    if (user.getLdapUserName() == null || user.getLdapTeamName() == null) {
      Pair<CrabTeam, CrabTeam.Mate> crabUser = getCrabUser(user.getSlackUserName());

      user.setLdapUserName(crabUser.getSecond().employee.login);
      if (!crabUser.getFirst().name.startsWith("Команда ")) {
        throw new RuntimeException("Team not found");
      }
      user.setLdapTeamName(crabUser.getFirst().name.substring(8));
      dao.updateUser(user);
    }

    return Mono.just(user);
  }

  public Pair<CrabTeam, CrabTeam.Mate> getCrabUser(String slackUserName) {
    List<CrabTeam> resp;
    try {
      resp = webClient.get()
          .uri(new URI("https://crab.pyn.ru/users"))
          .header("Content-Type", "application/json")
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<List<CrabTeam>>() {})
          .log()
          .block(Duration.ofMillis(1500));
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException("123");
    }

    return resp.stream()
        .map(team -> team.activeMembers.stream().map(mate -> Pair.of(team, mate)))
        .flatMap(Function.identity()) // это нужно
        .filter(item -> ("@" + slackUserName).equals(item.getSecond().employee.slack))
        .findFirst()
        .orElseThrow();
  }
}
