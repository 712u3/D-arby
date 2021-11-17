//package com.example.darby.dao;
//
//import com.example.darby.document.EstimationScale;
//import com.example.darby.document.User;
//import java.util.List;
//import org.springframework.data.r2dbc.repository.Query;
//import org.springframework.data.repository.reactive.ReactiveCrudRepository;
//import reactor.core.publisher.Flux;
//
//public interface H2Repository<T, ID> extends ReactiveCrudRepository<T, ID> {
//
//  @Query("select 1")
//  public Flux<Number> getValue();
//
//  @Query("select * from estimation_scale2")
//  Flux<EstimationScale> getAllEstimationScales2(String userId);
//}
