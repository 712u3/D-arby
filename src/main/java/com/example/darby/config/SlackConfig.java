package com.example.darby.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {
  @Bean
  public App slackApp(@Value("${xoxb-token}") String xoxbToken) {
    AppConfig appConfig = AppConfig.builder().singleTeamBotToken(xoxbToken).build();
    return new App(appConfig);
  }
}
