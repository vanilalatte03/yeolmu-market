package com.guingujig.yeolmumarket.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MyOrderListItemResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orderRepository, new YeolmuProperties(null, null));
  }

  @Test
  void 준비된_구매자와_상품으로_주문을_저장한다() {
    User buyer = user(2L, "buyer@example.com", "열무구매자");
    Product product = reservedProduct(10L, user(1L, "seller@example.com", "열무판매자"));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> persist(invocation.getArgument(0)));

    CreateOrderResponse response = orderService.createOrder(buyer, product);

    assertThat(response.orderId()).isEqualTo(100L);
    assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
    assertThat(response.product().productId()).isEqualTo(10L);
    assertThat(response.product().price()).isEqualTo(430000);
    assertThat(response.buyer().userId()).isEqualTo(2L);
    assertThat(response.seller().userId()).isEqualTo(1L);
    verify(orderRepository).save(any(Order.class));
  }

  @Test
  void 주문_상세는_참여자만_조회한다() {
    User buyer = user(2L, "buyer@example.com", "열무구매자");
    Product product = reservedProduct(10L, user(1L, "seller@example.com", "열무판매자"));
    Order order = persistedOrder(100L, buyer, product);
    when(orderRepository.findWithDetailsById(100L)).thenReturn(Optional.of(order));

    GetOrderResponse response = orderService.getOrder(buyer.getId(), 100L);

    assertThat(response.orderId()).isEqualTo(100L);
    assertThat(response.product().status()).isEqualTo(ProductStatus.RESERVED);
    assertThat(response.buyer().userId()).isEqualTo(buyer.getId());
  }

  @Test
  void 주문_상세_참여자가_아니면_ORDER_ACCESS_DENIED를_반환한다() {
    User buyer = user(2L, "buyer@example.com", "열무구매자");
    Product product = reservedProduct(10L, user(1L, "seller@example.com", "열무판매자"));
    Order order = persistedOrder(100L, buyer, product);
    when(orderRepository.findWithDetailsById(100L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.getOrder(99L, 100L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_주문은_ORDER_NOT_FOUND를_반환한다() {
    when(orderRepository.findWithDetailsById(100L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getExistingOrderWithDetails(100L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  void 구매_주문_목록은_상태_필터가_없으면_구매자_조회로_위임한다() {
    User buyer = user(2L, "buyer@example.com", "열무구매자");
    Product product = reservedProduct(10L, user(1L, "seller@example.com", "열무판매자"));
    Order order = persistedOrder(100L, buyer, product);
    when(orderRepository.findByBuyerId(any(), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

    PageResponse<MyOrderListItemResponse> response = orderService.getMyOrders(2L, 0, 10, null);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().orderId()).isEqualTo(100L);
    ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(orderRepository)
        .findByBuyerId(org.mockito.ArgumentMatchers.eq(2L), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending())
        .isTrue();
  }

  @Test
  void 판매_주문_목록_잘못된_페이지는_INVALID_PAGINATION을_반환한다() {
    assertThatThrownBy(() -> orderService.getMySales(1L, 0, 0, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  private Order persist(Order order) {
    ReflectionTestUtils.setField(order, "id", 100L);
    ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 6, 24, 10, 0));
    ReflectionTestUtils.setField(order, "modifiedAt", LocalDateTime.of(2026, 6, 24, 10, 0));
    return order;
  }

  private Order persistedOrder(Long orderId, User buyer, Product product) {
    Order order = Order.create(buyer, product);
    ReflectionTestUtils.setField(order, "id", orderId);
    ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 6, 24, 10, 0));
    ReflectionTestUtils.setField(order, "modifiedAt", LocalDateTime.of(2026, 6, 24, 10, 0));
    return order;
  }

  private Product reservedProduct(Long productId, User seller) {
    Product product =
        Product.create(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000, Category.create("디지털기기"));
    ReflectionTestUtils.setField(product, "id", productId);
    ReflectionTestUtils.setField(product, "status", ProductStatus.RESERVED);
    return product;
  }

  private User user(Long userId, String email, String nickname) {
    User user = new User(email, "encoded-password", nickname);
    ReflectionTestUtils.setField(user, "id", userId);
    return user;
  }
}
