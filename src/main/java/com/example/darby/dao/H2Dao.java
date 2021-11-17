//package com.example.darby.dao;
//
//import com.example.darby.document.EstimationScale;
//import com.example.darby.document.GameRoom;
//import java.util.List;
//import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
//import org.springframework.data.relational.core.query.Criteria;
//import org.springframework.data.relational.core.query.Query;
//import org.springframework.stereotype.Repository;
//import org.springframework.transaction.annotation.Transactional;
//
//@Repository
//public class H2Dao {
//
//  private final R2dbcEntityTemplate r2dbcTemplate;
//
//  public H2Dao(R2dbcEntityTemplate r2dbcTemplate) {
//    this.r2dbcTemplate = r2dbcTemplate;
//  }
//
////  @Transactional
//  public void createTable(String userId) {
//    var result1 = r2dbcTemplate.getDatabaseClient().sql("""
//          DROP TABLE IF EXISTS estimation_scale2
//        """)
//        .fetch().rowsUpdated().block();
//
//    var result2 = r2dbcTemplate.getDatabaseClient().sql("""
//          CREATE TABLE IF not EXISTS estimation_scale2(
//            id integer auto_increment PRIMARY KEY,
//            name VARCHAR(20),
//            marks array,
//            basic boolean
//          )
//        """)
//        .fetch()
//        .rowsUpdated()
//        .block();
//
//    var result3 = r2dbcTemplate.insert(EstimationScale.class)
//        .into("estimation_scale2")
//        .using(new EstimationScale("Последовательная", List.of("1", "2", "3", "4", "5", "6", "7", "8")))
//        .block();
//
//    var result4 = r2dbcTemplate.select(EstimationScale.class)
//        .from("estimation_scale2")
//        .first()
//        .block();
//
//    System.out.println(result4.getId());
//    System.out.println(result4.getMarks());
//    System.out.println(result4.getName());
//  }
//
//
//  public List<EstimationScale> getAllEstimationScales(String userId) {
//    Criteria criteria1 = Criteria.where("user_id").is(userId);
//    Query query1 = Query.query(criteria1);
//    var lastGameRoom = r2dbcTemplate.select(query1, GameRoom.class).last().block();
//
//    Criteria criteria2 = Criteria.where("basic").is(true);
//    if (lastGameRoom != null) {
//      criteria2 = Criteria.where("basic").is(true).or("id").is(lastGameRoom.getEstimationScaleId());
//    }
//    Query query2 = Query.query(criteria2);
//
//    return r2dbcTemplate.select(EstimationScale.class)
//        .from("estimation_scale2")
//        .matching(query2)
//        .all()
//        .collectList()
//        .block();
//  }
//}
