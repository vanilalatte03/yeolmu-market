package com.guingujig.yeolmumarket.domain.refund.repository;

import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

  boolean existsByOrder_Id(Long orderId);

  long countByOrder_Id(Long orderId);

  Optional<RefundRequest> findByOrder_Id(Long orderId);
}
