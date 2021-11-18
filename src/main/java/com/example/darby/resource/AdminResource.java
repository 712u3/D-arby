package com.example.darby.resource;

import com.example.darby.dao.H2Dao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin")
public class AdminResource {

  private final H2Dao dao;

  public AdminResource(H2Dao dao) {
    this.dao = dao;
  }

  @GetMapping("/clear-db")
  public Mono<Void> getEmployeeById() {
    dao.prepareDatabase();
    return Mono.empty();
  }
}
