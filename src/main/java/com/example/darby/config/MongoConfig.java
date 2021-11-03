package com.example.darby.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

@Configuration
public class MongoConfig {

//  @Bean
//  public ReactiveMongoTemplate reactiveMongoTemplate() {
//    MongoClient mongoClient = MongoClients.create();
//    return new ReactiveMongoTemplate(mongoClient, "test");
//  }
}
