//package com.example.darby;
//
//import com.example.darby.dao.H2Dao;
//import com.example.darby.dao.H2Repository;
//import javax.annotation.PostConstruct;
//import org.springframework.stereotype.Service;
//
//@Service
//public class TestService {
//
//  private final H2Dao h2Dao;
//  private final H2Repository h2Repository;
//
//  public TestService(H2Dao h2Dao, H2Repository h2Repository) {
//    this.h2Dao = h2Dao;
//    this.h2Repository = h2Repository;
//  }
//
//  @PostConstruct
//  public void test() {
////    User user = new User("123", "2342");
////    h2Repository.save(user);
////    var users = h2Repository.findAll().collectList().block();
//    h2Dao.createTable("123");
//    var aaa = h2Repository.getAllEstimationScales2("123").collectList().block();
////    System.out.println(aaa.get(0).getName());
////    System.out.println(aaa.get(0).getId());
////    System.out.println(aaa.get(0).getMarks());
//  }
//}
