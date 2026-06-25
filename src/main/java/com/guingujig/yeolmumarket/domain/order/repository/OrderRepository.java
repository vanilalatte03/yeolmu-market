package com.guingujig.yeolmumarket.domain.order.repository;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
