package com.example.darby;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // https://github.com/spring-projects/spring-data-mongodb/issues/2452
public class DarbyApplication {

  public static void main(String[] args) {
    SpringApplication.run(DarbyApplication.class, args);
  }

}
