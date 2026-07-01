package com.guingujig.yeolmumarket.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import com.guingujig.yeolmumarket.domain.search.service.SearchIndexVersionProvider;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("perf-seed")
@EnabledIfEnvironmentVariable(named = "YEOLMU_PERF_SEED", matches = "true")
class PerfDummyDataSeedTest {

  private static final Logger log = LoggerFactory.getLogger(PerfDummyDataSeedTest.class);
  private static final DateTimeFormatter RUN_KEY_FORMATTER =
      DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
  private static final int MIN_TABLE_ROW_COUNT = 2;
  private static final String CATEGORY_NAME_PREFIX = "PF";
  private static final String USER_EMAIL_PREFIX = "perf-user-";
  private static final String USER_EMAIL_DOMAIN = "@example.com";
  private static final String PRODUCT_TITLE_PREFIX = "[PERF]";
  private static final String DUMMY_PASSWORD =
      "$2a$10$7EqJtq98hPqEX7fNZaFWoOYoXb8nV4bGzH0LbK9Gp1c7RgaWw4p1W";

  private static final String INSERT_CATEGORY_SQL =
      """
      insert into category (name, created_at, modified_at)
      values (?, ?, ?)
      """;
  private static final String INSERT_USER_SQL =
      """
      insert into users (nickname, email, password, role, created_at, modified_at)
      values (?, ?, ?, 'USER', ?, ?)
      """;
  private static final String INSERT_PRODUCT_SQL =
      """
      insert into product (
        category_id, seller_id, title, description, price, status,
        hidden, version, created_at, modified_at, deleted_at
      )
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null)
      """;
  private static final String INSERT_PRODUCT_IMAGE_SQL =
      """
      insert into product_image (product_id, url, is_thumbnail, created_at)
      values (?, ?, true, ?)
      """;
  private static final String INSERT_WISH_SQL =
      """
      insert into wish (user_id, product_id, created_at)
      values (?, ?, ?)
      """;
  private static final String INSERT_CHAT_ROOM_SQL =
      """
      insert into chatroom (product_id, seller_id, buyer_id, created_at, last_message_at)
      values (?, ?, ?, ?, ?)
      """;
  private static final String INSERT_CHAT_MESSAGE_SQL =
      """
      insert into chatmessage (chatroom_id, sender_id, content, created_at, accepted_message_id)
      values (?, ?, ?, ?, ?)
      """;
  private static final String INSERT_ORDER_SQL =
      """
      insert into orders (
        buyer_id, seller_id, product_id, order_status, order_price,
        tracking_number, shipped_at, created_at, modified_at
      )
      values (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;
  private static final String INSERT_PAYMENT_SQL =
      """
      insert into payment (
        order_id, method, status, amount, idempotency_key,
        paid_at, failed_at, canceled_at, cancel_reason, created_at, modified_at
      )
      values (?, ?, ?, ?, ?, ?, null, null, null, ?, ?)
      """;
  private static final String INSERT_REFUND_REQUEST_SQL =
      """
      insert into refund_request (
        order_id, requester_id, reason, status, seller_response,
        requested_at, approved_at, rejected_at, resolved_at, created_at, modified_at
      )
      values (?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?)
      """;
  private static final String INSERT_REVIEW_SQL =
      """
      insert into review (order_id, reviewer_id, reviewee_id, score, content, created_at, modified_at)
      values (?, ?, ?, ?, ?, ?, ?)
      """;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private SearchIndexVersionProvider searchIndexVersionProvider;
  @Autowired private Environment environment;
  @Autowired private Clock clock;

  @Test
  void 모든_DB_테이블에_성능테스트용_더미_데이터를_JDBC_Batch로_적재한다() {
    SeedOptions options = seedOptions();
    long startedAt = System.nanoTime();
    String runKey = createRunKey();
    String productMarker = productMarker(runKey);

    insertUsers(runKey, options);
    List<Long> userIds = loadUserIds(runKey);
    requireRowCount("users", userIds, options);

    insertCategories(runKey, options);
    List<Long> categoryIds = loadCategoryIds(runKey);

    insertProducts(categoryIds, userIds, productMarker, options);
    List<ProductSeedRow> productRows = loadProductRows(productMarker);

    insertProductImages(productRows, options);
    insertWishes(productRows, userIds, options);
    insertChatRooms(productRows, userIds, options);
    List<ChatRoomSeedRow> chatRoomRows = loadChatRoomRows(productMarker);

    insertChatMessages(chatRoomRows, runKey, options);
    insertOrders(productRows, userIds, options);
    List<OrderSeedRow> orderRows = loadOrderRows(productMarker);

    insertPayments(orderRows, runKey, options);
    insertRefundRequests(orderRows, runKey, options);
    insertReviews(orderRows, runKey, options);
    searchIndexVersionProvider.increaseVersions(List.of(ProductStatus.ON_SALE));

    assertSeedCounts(runKey, productMarker, options);

    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
    log.info(
        "Performance dummy data seed completed. runKey={}, tableRows={}, batchSize={},"
            + " elapsedMillis={}",
        runKey,
        options.tableRowCount(),
        options.batchSize(),
        elapsedMillis);
  }

  private void insertUsers(String runKey, SeedOptions options) {
    batchInsert(
        INSERT_USER_SQL,
        options,
        (preparedStatement, sequence) -> {
          LocalDateTime createdAt = createdAt(sequence);
          preparedStatement.setString(1, userNickname(runKey, sequence));
          preparedStatement.setString(2, userEmail(runKey, sequence));
          preparedStatement.setString(3, DUMMY_PASSWORD);
          preparedStatement.setTimestamp(4, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(5, Timestamp.valueOf(createdAt));
        });
  }

  private List<Long> loadUserIds(String runKey) {
    return jdbcTemplate.queryForList(
        "select id from users where email like ? escape '!' order by id",
        Long.class,
        escapeLikePattern(userEmailPrefix(runKey)) + "%");
  }

  private void insertCategories(String runKey, SeedOptions options) {
    batchInsert(
        INSERT_CATEGORY_SQL,
        options,
        (preparedStatement, sequence) -> {
          LocalDateTime createdAt = createdAt(sequence);
          preparedStatement.setString(1, categoryName(runKey, sequence));
          preparedStatement.setTimestamp(2, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(3, Timestamp.valueOf(createdAt));
        });
  }

  private List<Long> loadCategoryIds(String runKey) {
    return jdbcTemplate.queryForList(
        "select id from category where name like ? escape '!' order by id",
        Long.class,
        escapeLikePattern(categoryNamePrefix(runKey)) + "%");
  }

  private void insertProducts(
      List<Long> categoryIds, List<Long> userIds, String productMarker, SeedOptions options) {
    requireRowCount("category", categoryIds, options);
    batchInsert(
        INSERT_PRODUCT_SQL,
        options,
        (preparedStatement, sequence) -> {
          LocalDateTime createdAt = createdAt(sequence);
          LocalDateTime modifiedAt = createdAt.plusHours(sequence % 24L);

          preparedStatement.setLong(1, categoryIds.get(sequence - 1));
          preparedStatement.setLong(2, sellerUserId(sequence, userIds));
          preparedStatement.setString(3, productTitle(productMarker, sequence));
          preparedStatement.setString(4, productDescription(sequence));
          preparedStatement.setInt(5, productPrice(sequence));
          preparedStatement.setString(6, ProductStatus.ON_SALE.name());
          preparedStatement.setBoolean(7, false);
          preparedStatement.setInt(8, 0);
          preparedStatement.setTimestamp(9, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(10, Timestamp.valueOf(modifiedAt));
        });
  }

  private List<ProductSeedRow> loadProductRows(String productMarker) {
    return jdbcTemplate.query(
        """
        select id, seller_id, price
        from product
        where title like ? escape '!'
        order by id
        """,
        (resultSet, rowNumber) ->
            new ProductSeedRow(
                resultSet.getLong("id"), resultSet.getLong("seller_id"), resultSet.getInt("price")),
        escapeLikePattern(productMarker) + "%");
  }

  private void insertProductImages(List<ProductSeedRow> productRows, SeedOptions options) {
    requireRowCount("product", productRows, options);
    batchInsertRows(
        INSERT_PRODUCT_IMAGE_SQL,
        productRows,
        options,
        (preparedStatement, product, sequence) -> {
          preparedStatement.setLong(1, product.id());
          preparedStatement.setString(2, "/uploads/perf/" + product.id() + ".jpg");
          preparedStatement.setTimestamp(3, Timestamp.valueOf(createdAt(sequence)));
        });
  }

  private void insertWishes(
      List<ProductSeedRow> productRows, List<Long> userIds, SeedOptions options) {
    requireRowCount("product", productRows, options);
    batchInsertRows(
        INSERT_WISH_SQL,
        productRows,
        options,
        (preparedStatement, product, sequence) -> {
          preparedStatement.setLong(1, buyerUserId(sequence, userIds));
          preparedStatement.setLong(2, product.id());
          preparedStatement.setTimestamp(3, Timestamp.valueOf(createdAt(sequence)));
        });
  }

  private void insertChatRooms(
      List<ProductSeedRow> productRows, List<Long> userIds, SeedOptions options) {
    requireRowCount("product", productRows, options);
    batchInsertRows(
        INSERT_CHAT_ROOM_SQL,
        productRows,
        options,
        (preparedStatement, product, sequence) -> {
          LocalDateTime createdAt = createdAt(sequence);
          preparedStatement.setLong(1, product.id());
          preparedStatement.setLong(2, product.sellerId());
          preparedStatement.setLong(3, buyerUserId(sequence, userIds));
          preparedStatement.setTimestamp(4, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(5, Timestamp.valueOf(createdAt.plusMinutes(1)));
        });
  }

  private List<ChatRoomSeedRow> loadChatRoomRows(String productMarker) {
    return jdbcTemplate.query(
        """
        select chatroom.id, chatroom.buyer_id, chatroom.seller_id
        from chatroom
        join product on product.id = chatroom.product_id
        where product.title like ? escape '!'
        order by chatroom.id
        """,
        (resultSet, rowNumber) ->
            new ChatRoomSeedRow(
                resultSet.getLong("id"),
                resultSet.getLong("buyer_id"),
                resultSet.getLong("seller_id")),
        escapeLikePattern(productMarker) + "%");
  }

  private void insertChatMessages(
      List<ChatRoomSeedRow> chatRoomRows, String runKey, SeedOptions options) {
    requireRowCount("chatroom", chatRoomRows, options);
    batchInsertRows(
        INSERT_CHAT_MESSAGE_SQL,
        chatRoomRows,
        options,
        (preparedStatement, chatRoom, sequence) -> {
          preparedStatement.setLong(1, chatRoom.id());
          preparedStatement.setLong(2, chatMessageSenderId(chatRoom, sequence));
          preparedStatement.setString(3, "Performance chat message " + runKey + " " + sequence);
          preparedStatement.setTimestamp(4, Timestamp.valueOf(createdAt(sequence).plusMinutes(1)));
          preparedStatement.setString(5, acceptedMessageId(runKey, sequence));
        });
  }

  private void insertOrders(
      List<ProductSeedRow> productRows, List<Long> userIds, SeedOptions options) {
    requireRowCount("product", productRows, options);
    batchInsertRows(
        INSERT_ORDER_SQL,
        productRows,
        options,
        (preparedStatement, product, sequence) -> {
          LocalDateTime createdAt = createdAt(sequence);
          LocalDateTime shippedAt = createdAt.plusDays(1);

          preparedStatement.setLong(1, buyerUserId(sequence, userIds));
          preparedStatement.setLong(2, product.sellerId());
          preparedStatement.setLong(3, product.id());
          preparedStatement.setString(4, OrderStatus.COMPLETED.name());
          preparedStatement.setInt(5, product.price());
          preparedStatement.setString(6, trackingNumber(sequence));
          preparedStatement.setTimestamp(7, Timestamp.valueOf(shippedAt));
          preparedStatement.setTimestamp(8, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(9, Timestamp.valueOf(shippedAt.plusDays(2)));
        });
  }

  private List<OrderSeedRow> loadOrderRows(String productMarker) {
    return jdbcTemplate.query(
        """
        select orders.id, orders.buyer_id, orders.seller_id, orders.order_price
        from orders
        join product on product.id = orders.product_id
        where product.title like ? escape '!'
        order by orders.id
        """,
        (resultSet, rowNumber) ->
            new OrderSeedRow(
                resultSet.getLong("id"),
                resultSet.getLong("buyer_id"),
                resultSet.getLong("seller_id"),
                resultSet.getInt("order_price")),
        escapeLikePattern(productMarker) + "%");
  }

  private void insertPayments(List<OrderSeedRow> orderRows, String runKey, SeedOptions options) {
    requireRowCount("orders", orderRows, options);
    batchInsertRows(
        INSERT_PAYMENT_SQL,
        orderRows,
        options,
        (preparedStatement, order, sequence) -> {
          LocalDateTime paidAt = createdAt(sequence).plusHours(1);
          preparedStatement.setLong(1, order.id());
          preparedStatement.setString(2, PaymentMethod.MOCK_CARD.name());
          preparedStatement.setString(3, PaymentStatus.PAID.name());
          preparedStatement.setInt(4, order.orderPrice());
          preparedStatement.setString(5, idempotencyKey(runKey, sequence));
          preparedStatement.setTimestamp(6, Timestamp.valueOf(paidAt));
          preparedStatement.setTimestamp(7, Timestamp.valueOf(paidAt));
          preparedStatement.setTimestamp(8, Timestamp.valueOf(paidAt));
        });
  }

  private void insertRefundRequests(
      List<OrderSeedRow> orderRows, String runKey, SeedOptions options) {
    requireRowCount("orders", orderRows, options);
    batchInsertRows(
        INSERT_REFUND_REQUEST_SQL,
        orderRows,
        options,
        (preparedStatement, order, sequence) -> {
          LocalDateTime requestedAt = createdAt(sequence).plusDays(2);
          LocalDateTime rejectedAt = requestedAt.plusHours(3);
          LocalDateTime resolvedAt = rejectedAt.plusHours(12);

          preparedStatement.setLong(1, order.id());
          preparedStatement.setLong(2, order.buyerId());
          preparedStatement.setString(3, "Performance refund request " + runKey + " " + sequence);
          preparedStatement.setString(4, RefundRequestStatus.CLOSED.name());
          preparedStatement.setString(5, "Performance dispute closed");
          preparedStatement.setTimestamp(6, Timestamp.valueOf(requestedAt));
          preparedStatement.setTimestamp(7, Timestamp.valueOf(rejectedAt));
          preparedStatement.setTimestamp(8, Timestamp.valueOf(resolvedAt));
          preparedStatement.setTimestamp(9, Timestamp.valueOf(requestedAt));
          preparedStatement.setTimestamp(10, Timestamp.valueOf(resolvedAt));
        });
  }

  private void insertReviews(List<OrderSeedRow> orderRows, String runKey, SeedOptions options) {
    requireRowCount("orders", orderRows, options);
    batchInsertRows(
        INSERT_REVIEW_SQL,
        orderRows,
        options,
        (preparedStatement, order, sequence) -> {
          LocalDateTime createdAt = createdAt(sequence).plusDays(4);
          preparedStatement.setLong(1, order.id());
          preparedStatement.setLong(2, order.buyerId());
          preparedStatement.setLong(3, order.sellerId());
          preparedStatement.setInt(4, 1 + Math.floorMod(sequence, 5));
          preparedStatement.setString(5, "Performance review " + runKey + " " + sequence);
          preparedStatement.setTimestamp(6, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(7, Timestamp.valueOf(createdAt));
        });
  }

  private void batchInsert(
      String sql, SeedOptions options, SequencePreparedStatementBinder preparedStatementBinder) {
    for (int start = 1; start <= options.tableRowCount(); start += options.batchSize()) {
      int batchSize = Math.min(options.batchSize(), options.tableRowCount() - start + 1);
      int startInclusive = start;
      transactionTemplate.executeWithoutResult(
          status ->
              jdbcTemplate.batchUpdate(
                  sql,
                  new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement preparedStatement, int index)
                        throws SQLException {
                      preparedStatementBinder.bind(preparedStatement, startInclusive + index);
                    }

                    @Override
                    public int getBatchSize() {
                      return batchSize;
                    }
                  }));
    }
  }

  private <T> void batchInsertRows(
      String sql,
      List<T> rows,
      SeedOptions options,
      RowPreparedStatementBinder<T> preparedStatementBinder) {
    for (int start = 0; start < rows.size(); start += options.batchSize()) {
      int fromIndex = start;
      int toIndex = Math.min(start + options.batchSize(), rows.size());
      List<T> batchRows = rows.subList(fromIndex, toIndex);
      transactionTemplate.executeWithoutResult(
          status ->
              jdbcTemplate.batchUpdate(
                  sql,
                  new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement preparedStatement, int index)
                        throws SQLException {
                      preparedStatementBinder.bind(
                          preparedStatement, batchRows.get(index), fromIndex + index + 1);
                    }

                    @Override
                    public int getBatchSize() {
                      return batchRows.size();
                    }
                  }));
    }
  }

  private void assertSeedCounts(String runKey, String productMarker, SeedOptions options) {
    long expectedCount = options.tableRowCount();
    assertEquals(expectedCount, countUsers(runKey));
    assertEquals(expectedCount, countCategories(runKey));
    assertEquals(expectedCount, countProducts(productMarker));
    assertEquals(expectedCount, countProductImages(productMarker));
    assertEquals(expectedCount, countWishes(productMarker));
    assertEquals(expectedCount, countChatRooms(productMarker));
    assertEquals(expectedCount, countChatMessages(productMarker));
    assertEquals(expectedCount, countOrders(productMarker));
    assertEquals(expectedCount, countPayments(productMarker));
    assertEquals(expectedCount, countRefundRequests(productMarker));
    assertEquals(expectedCount, countReviews(productMarker));
  }

  private long countCategories(String runKey) {
    return queryCount(
        "select count(*) from category where name like ? escape '!'",
        escapeLikePattern(categoryNamePrefix(runKey)) + "%");
  }

  private long countUsers(String runKey) {
    return queryCount(
        "select count(*) from users where email like ? escape '!'",
        escapeLikePattern(userEmailPrefix(runKey)) + "%");
  }

  private long countProducts(String productMarker) {
    return queryCount(
        "select count(*) from product where title like ? escape '!'",
        escapeLikePattern(productMarker) + "%");
  }

  private long countProductImages(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from product_image image
        join product on product.id = image.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countWishes(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from wish
        join product on product.id = wish.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countChatRooms(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from chatroom
        join product on product.id = chatroom.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countChatMessages(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from chatmessage message
        join chatroom on chatroom.id = message.chatroom_id
        join product on product.id = chatroom.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countOrders(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from orders
        join product on product.id = orders.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countPayments(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from payment
        join orders on orders.id = payment.order_id
        join product on product.id = orders.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countRefundRequests(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from refund_request refund
        join orders on orders.id = refund.order_id
        join product on product.id = orders.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countReviews(String productMarker) {
    return countRowsJoinedByProduct(
        """
        select count(*)
        from review
        join orders on orders.id = review.order_id
        join product on product.id = orders.product_id
        where product.title like ? escape '!'
        """,
        productMarker);
  }

  private long countRowsJoinedByProduct(String sql, String productMarker) {
    return queryCount(sql, escapeLikePattern(productMarker) + "%");
  }

  private long queryCount(String sql, String likePattern) {
    Long count = jdbcTemplate.queryForObject(sql, Long.class, likePattern);
    return count == null ? 0L : count;
  }

  private SeedOptions seedOptions() {
    int legacyProductCount =
        environment.getProperty("yeolmu.perf-dummy-data.product-count", Integer.class, 50_000);
    return new SeedOptions(
        atLeastIntProperty(
            "yeolmu.perf-dummy-data.table-row-count", legacyProductCount, MIN_TABLE_ROW_COUNT),
        positiveIntProperty("yeolmu.perf-dummy-data.batch-size", 1_000));
  }

  private int positiveIntProperty(String key, int defaultValue) {
    int value = environment.getProperty(key, Integer.class, defaultValue);
    if (value < 1) {
      throw new IllegalArgumentException(key + " must be positive.");
    }
    return value;
  }

  private int atLeastIntProperty(String key, int defaultValue, int minimumValue) {
    int value = environment.getProperty(key, Integer.class, defaultValue);
    if (value < minimumValue) {
      throw new IllegalArgumentException(key + " must be at least " + minimumValue + ".");
    }
    return value;
  }

  private void requireRowCount(String tableName, List<?> rows, SeedOptions options) {
    if (rows.size() != options.tableRowCount()) {
      throw new IllegalStateException(
          "Perf seed row count mismatch. table="
              + tableName
              + ", expected="
              + options.tableRowCount()
              + ", actual="
              + rows.size());
    }
  }

  private String createRunKey() {
    return RUN_KEY_FORMATTER.format(clock.instant()) + UUID.randomUUID().toString().substring(0, 6);
  }

  private String productMarker(String runKey) {
    return PRODUCT_TITLE_PREFIX + " " + runKey;
  }

  private String categoryNamePrefix(String runKey) {
    return CATEGORY_NAME_PREFIX + runKey + "-";
  }

  private String categoryName(String runKey, int sequence) {
    return categoryNamePrefix(runKey) + Integer.toString(sequence, Character.MAX_RADIX);
  }

  private String userEmailPrefix(String runKey) {
    return USER_EMAIL_PREFIX + runKey + "-";
  }

  private String userEmail(String runKey, int sequence) {
    return userEmailPrefix(runKey) + "%05d".formatted(sequence) + USER_EMAIL_DOMAIN;
  }

  private String userNickname(String runKey, int sequence) {
    return "perf-" + runKey + "-" + "%05d".formatted(sequence);
  }

  private String productTitle(String productMarker, int sequence) {
    return "%s %06d %s".formatted(productMarker, sequence, keyword(sequence));
  }

  private String productDescription(int sequence) {
    return "Performance test product description "
        + sequence
        + ". keyword="
        + keyword(sequence)
        + ", condition=used, market=yeolmu.";
  }

  private int productPrice(int sequence) {
    return 1_000 + Math.floorMod(sequence * 37, 2_000_000);
  }

  private String trackingNumber(int sequence) {
    return "PERF-TRACK-" + "%06d".formatted(sequence);
  }

  private String idempotencyKey(String runKey, int sequence) {
    return "perf-payment-" + runKey + "-" + "%06d".formatted(sequence);
  }

  private String acceptedMessageId(String runKey, int sequence) {
    return "pm-" + runKey + "-" + "%06d".formatted(sequence);
  }

  private Long sellerUserId(int sequence, List<Long> userIds) {
    return userIds.get(Math.floorMod(sequence - 1, userIds.size()));
  }

  private Long buyerUserId(int sequence, List<Long> userIds) {
    return userIds.get(Math.floorMod(sequence, userIds.size()));
  }

  private Long chatMessageSenderId(ChatRoomSeedRow chatRoom, int sequence) {
    if (Math.floorMod(sequence, 2) == 0) {
      return chatRoom.sellerId();
    }
    return chatRoom.buyerId();
  }

  private String keyword(int sequence) {
    String[] keywords = {
      "iphone", "macbook", "chair", "desk", "bicycle",
      "camera", "bag", "keyboard", "monitor", "earphone"
    };
    return keywords[Math.floorMod(sequence, keywords.length)];
  }

  private LocalDateTime createdAt(int sequence) {
    return nowUtc().minusDays(sequence % 365L).minusSeconds(sequence % 86_400L);
  }

  private LocalDateTime nowUtc() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private String escapeLikePattern(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private record SeedOptions(int tableRowCount, int batchSize) {}

  private record ProductSeedRow(Long id, Long sellerId, Integer price) {}

  private record ChatRoomSeedRow(Long id, Long buyerId, Long sellerId) {}

  private record OrderSeedRow(Long id, Long buyerId, Long sellerId, Integer orderPrice) {}

  @FunctionalInterface
  private interface SequencePreparedStatementBinder {
    void bind(PreparedStatement preparedStatement, int sequence) throws SQLException;
  }

  @FunctionalInterface
  private interface RowPreparedStatementBinder<T> {
    void bind(PreparedStatement preparedStatement, T row, int sequence) throws SQLException;
  }
}
