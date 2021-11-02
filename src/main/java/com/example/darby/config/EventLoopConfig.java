package com.example.darby.config;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

@Configuration
public class EventLoopConfig {

  @Bean
  @Primary
  @Qualifier("reactorResourceFactoryMain")
  public ReactorResourceFactory reactorResourceFactory(@Qualifier("poolForPostToSlackApi") LoopResources loopResources) {
    ReactorResourceFactory reactorResourceFactory = new ReactorResourceFactory();
    reactorResourceFactory.setUseGlobalResources(false);
    reactorResourceFactory.setLoopResources(loopResources);

    return reactorResourceFactory;
  }

  @Bean
  @Qualifier("reactorResourceFactoryWs")
  public ReactorResourceFactory reactorResourceFactory2(@Qualifier("poolForWebSocket") LoopResources loopResources) {
    ReactorResourceFactory reactorResourceFactory = new ReactorResourceFactory();
    reactorResourceFactory.setUseGlobalResources(false);
    reactorResourceFactory.setLoopResources(loopResources);

    return reactorResourceFactory;
  }

  @Bean
  @Qualifier("poolForPostToSlackApi")
  public LoopResources loopResources1() {
    return b -> getNioEventLoopGroup("my-slackapi", 1);
//    return LoopResources.create("my-eventloop", 1, true);
  }

  @Bean
  @Qualifier("poolForWebSocket")
  public LoopResources loopResources2() {
    return b -> getNioEventLoopGroup("my-websocket", 1);
  }

  private NioEventLoopGroup getNioEventLoopGroup(String poolName, int nThreads) {
    ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        nThreads,
        nThreads,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new DefaultThreadFactory(poolName, true), // , Thread.MAX_PRIORITY
        new ThreadPoolExecutor.AbortPolicy());

    return new NioEventLoopGroup(nThreads, EXECUTOR);
  }

  @Bean
  public WebClient webClient(@Qualifier("reactorResourceFactoryMain") ReactorResourceFactory reactorResourceFactory) {
    LoopResources loopResources = reactorResourceFactory.getLoopResources();
    ConnectionProvider connectionProvider = reactorResourceFactory.getConnectionProvider();
    HttpClient httpClientMain = HttpClient.create(connectionProvider).runOn(loopResources);
    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClientMain);
    return WebClient.builder().clientConnector(connector).build();

    // автоматом оно хер знает что юзает и создает 2*cpu тредов
    // return WebClient.create();

  }

  @Bean
  public WebSocketClient webSocketClient(@Qualifier("reactorResourceFactoryWs") ReactorResourceFactory reactorResourceFactory) {
    LoopResources loopResources = reactorResourceFactory.getLoopResources();
    ConnectionProvider connectionProvider = reactorResourceFactory.getConnectionProvider();
    HttpClient httpClientWs = HttpClient.create(connectionProvider).runOn(loopResources);
    return new ReactorNettyWebSocketClient(httpClientWs);
  }
}
