package com.guingujig.yeolmumarket.global.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.order.service.OrderService;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import com.guingujig.yeolmumarket.support.TestDataCleaner;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
    properties = {
      "yeolmu.lock.redisson.enabled=true",
      "yeolmu.lock.wait-time=20ms",
      "yeolmu.lock.lease-time=2s"
    })
@EnabledIfEnvironmentVariable(named = "YEOLMU_REDIS_LOCK_TEST", matches = "true")
class DistributedLockRedisIntegrationTest {

  private final RedissonClient redissonClient;
  private final OrderService orderService;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TestDataCleaner testDataCleaner;

  @Autowired
  DistributedLockRedisIntegrationTest(
      RedissonClient redissonClient,
      OrderService orderService,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TestDataCleaner testDataCleaner) {
    this.redissonClient = redissonClient;
    this.orderService = orderService;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.testDataCleaner = testDataCleaner;
  }

  @BeforeEach
  void setUp() {
    testDataCleaner.deleteAll();
  }

  @AfterEach
  void tearDown() {
    testDataCleaner.deleteAll();
  }

  @Test
  void Order_락을_이미_보유_중이면_상태_변경은_CONFLICT로_거절된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product =
        ProductTestFactory.saveProduct(
            productRepository, categoryRepository, seller, "아이패드 미니", "생활기스 있습니다.", 430000);
    Order order = orderRepository.saveAndFlush(Order.create(buyer, product));

    RLock lock = redissonClient.getLock(LockKeys.order(order.getId()));
    lock.lock(2, TimeUnit.SECONDS);
    try {
      assertThatThrownBy(() -> orderService.cancelOrder(buyer.getId(), order.getId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }

    Order unchangedOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(unchangedOrder.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  @TestConfiguration
  static class RedisTestConfig {

    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(Environment environment) {
      String host = environment.getProperty("spring.data.redis.host", "localhost");
      int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379);
      Config config = new Config();
      config.useSingleServer().setAddress("redis://" + host + ":" + port);
      return Redisson.create(config);
    }
  }
}
