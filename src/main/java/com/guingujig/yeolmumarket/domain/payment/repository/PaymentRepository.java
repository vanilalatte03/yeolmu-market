package com.guingujig.yeolmumarket.domain.payment.repository;

import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  @EntityGraph(attributePaths = {"order", "order.product"})
  Optional<Payment> findByOrder_Id(Long orderId);

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
