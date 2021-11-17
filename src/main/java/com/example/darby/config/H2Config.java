//package com.example.darby.config;
//
//import io.r2dbc.h2.H2ConnectionConfiguration;
//import io.r2dbc.h2.H2ConnectionFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
//import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
//
//@Configuration
//@EnableR2dbcRepositories(basePackages = "com.example.darby.repository")
//public class H2Config {
//
//  @Bean
//  public H2ConnectionFactory connectionFactory() {
//    H2ConnectionConfiguration connectionConfiguration = H2ConnectionConfiguration.builder()
//        .file("/Users/l.vinogradov/projects/D-arby/database")
//        .username("sa")
//        .password("")
//        .option("DB_CLOSE_DELAY=-1")
//        .build();
//
//    H2ConnectionFactory connectionFactory = new H2ConnectionFactory(connectionConfiguration);
//    connectionFactory.create();
//    return connectionFactory;
//  }
//
//  @Bean
//  public R2dbcEntityTemplate r2dbcEntityTemplate(H2ConnectionFactory connectionFactory) {
//    return new R2dbcEntityTemplate(connectionFactory);
//  }
//}
