package com.guingujig.yeolmumarket.domain.order.repository;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  @EntityGraph(attributePaths = {"buyer", "seller", "product"})
  Optional<Order> findWithDetailsById(Long id);

  @EntityGraph(attributePaths = {"seller", "product"})
  Page<Order> findByBuyerId(Long buyerId, Pageable pageable);

  @EntityGraph(attributePaths = {"seller", "product"})
  Page<Order> findByBuyerIdAndOrderStatus(Long buyerId, OrderStatus orderStatus, Pageable pageable);

  @EntityGraph(attributePaths = {"buyer", "product"})
  Page<Order> findBySellerId(Long sellerId, Pageable pageable);

  @EntityGraph(attributePaths = {"buyer", "product"})
  Page<Order> findBySellerIdAndOrderStatus(
      Long sellerId, OrderStatus orderStatus, Pageable pageable);
}
