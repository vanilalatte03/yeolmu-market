package com.guingujig.yeolmumarket.domain.order.service;

import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;

  /**
   * 로그인한 구매자가 판매 중인 상품을 주문한다.
   *
   * <p>주문 생성과 상품 상태 변경(ON_SALE → RESERVED)을 하나의 트랜잭션에서 처리한다. Product.@Version 낙관적 락으로 동시 주문 충돌을
   * 감지하며, 충돌 시 GlobalExceptionHandler가 재시도 없이 409로 응답한다.
   *
   * @throws BusinessException PRODUCT_NOT_FOUND - 상품이 존재하지 않거나 삭제/숨김 처리된 경우
   * @throws BusinessException CANNOT_ORDER_OWN_PRODUCT - 자신의 상품을 주문하려는 경우
   * @throws BusinessException PRODUCT_NOT_ON_SALE - 상품이 ON_SALE 상태가 아닌 경우
   */
  @Transactional
  public CreateOrderResponse createOrder(Long buyerId, Long productId) {
    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Product product =
        productRepository
            .findWithSellerById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    if (product.isDeleted() || product.isHidden()) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
    }

    if (Objects.equals(product.getSeller().getId(), buyerId)) {
      throw new BusinessException(ErrorCode.CANNOT_ORDER_OWN_PRODUCT);
    }

    if (product.getStatus() != ProductStatus.ON_SALE) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    product.reserve();

    Order order = Order.create(buyer, product.getSeller(), product);
    orderRepository.save(order);

    return CreateOrderResponse.from(order);
  }
}
