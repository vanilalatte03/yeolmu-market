package com.guingujig.yeolmumarket.domain.payment.repository;

import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  @EntityGraph(attributePaths = {"order", "order.product"})
  Optional<Payment> findByOrder_Id(Long orderId);

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  @EntityGraph(attributePaths = {"order", "order.buyer", "order.seller"})
  Optional<Payment> findWithOrderAndUsersById(Long id);

  @EntityGraph(attributePaths = {"order", "order.buyer", "order.seller", "order.product"})
  Optional<Payment> findWithOrderBuyerSellerAndProductById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"order", "order.buyer", "order.seller", "order.product"})
  @Query("SELECT p FROM Payment p WHERE p.id = :id")
  Optional<Payment> findWithOrderBuyerSellerAndProductByIdForUpdate(@Param("id") Long id);
}
