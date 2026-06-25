package com.guingujig.yeolmumarket.domain.order.repository;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  @EntityGraph(attributePaths = {"buyer", "seller", "product"})
  Optional<Order> findWithDetailsById(Long id);
}
