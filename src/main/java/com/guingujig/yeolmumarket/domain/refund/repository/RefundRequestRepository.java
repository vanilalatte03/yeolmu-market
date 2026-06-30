package com.guingujig.yeolmumarket.domain.refund.repository;

import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

  boolean existsByOrder_Id(Long orderId);

  long countByOrder_Id(Long orderId);

  Optional<RefundRequest> findByOrder_Id(Long orderId);

  @Query("SELECT r.order.id FROM RefundRequest r WHERE r.id = :id")
  Optional<Long> findOrderIdById(@Param("id") Long id);

  @EntityGraph(attributePaths = {"order", "order.seller", "order.product"})
  @Query("SELECT r FROM RefundRequest r WHERE r.id = :id")
  Optional<RefundRequest> findWithOrderSellerAndProductById(@Param("id") Long id);
}
