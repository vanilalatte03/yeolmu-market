package com.guingujig.yeolmumarket.global.seed;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.search.service.SearchIndexVersionProvider;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("demo-seed")
public class DemoCatalogSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoCatalogSeeder.class);
  private static final DateTimeFormatter RUN_KEY_FORMATTER =
      DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);
  private static final Pattern RUN_KEY_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,40}");
  private static final String DUMMY_RAW_PASSWORD = "password";
  private static final String DEMO_SELLER_EMAIL_PREFIX = "demo-seller-";
  private static final String DEMO_EMAIL_DOMAIN = "@example.com";
  private static final String DEMO_PRODUCT_TITLE_PREFIX = "[DEMO]";
  private static final List<String> DEFAULT_CATEGORY_NAMES =
      List.of("디지털기기", "생활가전", "가구/인테리어", "생활/주방", "의류", "잡화", "도서/음반", "스포츠/레저", "유아동", "기타");
  private static final String[] PRODUCT_NAMES = {
    "아이폰 13 미니",
    "맥북 에어",
    "캠핑 의자",
    "원목 책상",
    "자전거",
    "에어팟 프로",
    "패딩",
    "기계식 키보드",
    "모니터",
    "필름 카메라",
    "전기포트",
    "아기 장난감"
  };

  private static final String INSERT_CATEGORY_SQL =
      """
      INSERT IGNORE INTO category (name, created_at, modified_at)
      VALUES (?, ?, ?)
      """;
  private static final String INSERT_USER_SQL =
      """
      INSERT IGNORE INTO users (nickname, email, password, role, created_at, modified_at)
      VALUES (?, ?, ?, 'USER', ?, ?)
      """;
  private static final String INSERT_PRODUCT_SQL =
      """
      INSERT INTO product (
        category_id, seller_id, title, description, price, status,
        hidden, version, created_at, modified_at, deleted_at
      )
      VALUES (?, ?, ?, ?, ?, ?, false, 0, ?, ?, null)
      """;

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final PasswordEncoder passwordEncoder;
  private final SearchIndexVersionProvider searchIndexVersionProvider;
  private final Environment environment;
  private final Clock clock;

  public DemoCatalogSeeder(
      JdbcTemplate jdbcTemplate,
      TransactionTemplate transactionTemplate,
      PasswordEncoder passwordEncoder,
      SearchIndexVersionProvider searchIndexVersionProvider,
      Environment environment,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
    this.passwordEncoder = passwordEncoder;
    this.searchIndexVersionProvider = searchIndexVersionProvider;
    this.environment = environment;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    SeedOptions options = seedOptions();
    if (!options.enabled()) {
      log.warn("Demo catalog seed skipped. Set YEOLMU_DEMO_SEED=true to run it.");
      return;
    }

    long startedAt = System.nanoTime();
    LocalDateTime baseTime = nowUtc();
    log.info(
        "Demo catalog seed started. runKey={}, products={}, sellers={}, batchSize={}",
        options.runKey(),
        options.productCount(),
        options.sellerCount(),
        options.batchSize());

    insertDefaultCategories(baseTime, options);
    List<Long> categoryIds = loadDefaultCategoryIds();
    insertSellers(baseTime, options);
    List<Long> sellerIds = loadSellerIds(options.runKey());
    requireRowCount("users", sellerIds.size(), options.sellerCount());

    String productMarker = productMarker(options.runKey());
    long existingProducts = countProducts(productMarker);
    if (existingProducts == 0) {
      insertProducts(baseTime, categoryIds, sellerIds, productMarker, options);
    } else if (existingProducts == options.productCount()) {
      log.info(
          "Demo products already exist for runKey={}. Product insert skipped.", options.runKey());
    } else {
      throw new IllegalStateException(
          "runKey="
              + options.runKey()
              + " 상품 데이터가 일부만 존재합니다. 새 YEOLMU_DEMO_RUN_KEY로 다시 실행하세요. existing="
              + existingProducts
              + ", expected="
              + options.productCount());
    }

    long productCount = countProducts(productMarker);
    requireRowCount("product", productCount, options.productCount());
    searchIndexVersionProvider.increaseVersions(List.of(ProductStatus.ON_SALE));

    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
    log.info(
        "Demo catalog seed completed. runKey={}, products={}, sellers={}, elapsedMillis={}",
        options.runKey(),
        productCount,
        sellerIds.size(),
        elapsedMillis);
    log.info(
        "Demo seller login example: email={}, password={}",
        sellerEmail(options.runKey(), 1),
        DUMMY_RAW_PASSWORD);
  }

  private void insertDefaultCategories(LocalDateTime baseTime, SeedOptions options) {
    batchInsert(
        INSERT_CATEGORY_SQL,
        DEFAULT_CATEGORY_NAMES.size(),
        options.batchSize(),
        (preparedStatement, sequence) -> {
          preparedStatement.setString(1, DEFAULT_CATEGORY_NAMES.get(sequence - 1));
          preparedStatement.setTimestamp(2, Timestamp.valueOf(baseTime));
          preparedStatement.setTimestamp(3, Timestamp.valueOf(baseTime));
        });
  }

  private List<Long> loadDefaultCategoryIds() {
    return DEFAULT_CATEGORY_NAMES.stream()
        .map(
            name -> {
              Long categoryId =
                  jdbcTemplate.queryForObject(
                      "SELECT id FROM category WHERE name = ?", Long.class, name);
              if (categoryId == null) {
                throw new IllegalStateException("기본 카테고리 생성에 실패했습니다. name=" + name);
              }
              return categoryId;
            })
        .toList();
  }

  private void insertSellers(LocalDateTime baseTime, SeedOptions options) {
    String dummyPassword = passwordEncoder.encode(DUMMY_RAW_PASSWORD);
    batchInsert(
        INSERT_USER_SQL,
        options.sellerCount(),
        options.batchSize(),
        (preparedStatement, sequence) -> {
          LocalDateTime createdAt = createdAt(baseTime, sequence);
          preparedStatement.setString(1, sellerNickname(sequence));
          preparedStatement.setString(2, sellerEmail(options.runKey(), sequence));
          preparedStatement.setString(3, dummyPassword);
          preparedStatement.setTimestamp(4, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(5, Timestamp.valueOf(createdAt));
        });
  }

  private List<Long> loadSellerIds(String runKey) {
    return jdbcTemplate.queryForList(
        "SELECT id FROM users WHERE email LIKE ? ESCAPE '!' ORDER BY id",
        Long.class,
        escapeLikePattern(sellerEmailPrefix(runKey)) + "%");
  }

  private void insertProducts(
      LocalDateTime baseTime,
      List<Long> categoryIds,
      List<Long> sellerIds,
      String productMarker,
      SeedOptions options) {
    batchInsert(
        INSERT_PRODUCT_SQL,
        options.productCount(),
        options.batchSize(),
        (preparedStatement, sequence) -> {
          LocalDateTime createdAt = createdAt(baseTime, sequence);
          preparedStatement.setLong(
              1, categoryIds.get(Math.floorMod(sequence - 1, categoryIds.size())));
          preparedStatement.setLong(
              2, sellerIds.get(Math.floorMod(sequence - 1, sellerIds.size())));
          preparedStatement.setString(3, productTitle(productMarker, sequence));
          preparedStatement.setString(4, productDescription(sequence));
          preparedStatement.setInt(5, productPrice(sequence));
          preparedStatement.setString(6, ProductStatus.ON_SALE.name());
          preparedStatement.setTimestamp(7, Timestamp.valueOf(createdAt));
          preparedStatement.setTimestamp(
              8, Timestamp.valueOf(createdAt.plusMinutes(sequence % 60L)));
        });
  }

  private void batchInsert(
      String sql,
      int rowCount,
      int configuredBatchSize,
      SequencePreparedStatementBinder preparedStatementBinder) {
    for (int start = 1; start <= rowCount; start += configuredBatchSize) {
      int batchSize = Math.min(configuredBatchSize, rowCount - start + 1);
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

  private long countProducts(String productMarker) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product WHERE title LIKE ? ESCAPE '!'",
            Long.class,
            escapeLikePattern(productMarker) + "%");
    return count == null ? 0 : count;
  }

  private void requireRowCount(String tableName, long actualCount, int expectedCount) {
    if (actualCount < expectedCount) {
      throw new IllegalStateException(
          tableName
              + " seed row count is too small. expected="
              + expectedCount
              + ", actual="
              + actualCount);
    }
  }

  private SeedOptions seedOptions() {
    boolean enabled = environment.getProperty("yeolmu.demo-seed.enabled", Boolean.class, false);
    int productCount = intProperty("yeolmu.demo-seed.product-count", 5_000);
    int sellerCount = intProperty("yeolmu.demo-seed.seller-count", 100);
    int batchSize = intProperty("yeolmu.demo-seed.batch-size", 1_000);
    String configuredRunKey = environment.getProperty("yeolmu.demo-seed.run-key", "");
    String runKey =
        configuredRunKey == null || configuredRunKey.isBlank() ? createRunKey() : configuredRunKey;

    if (productCount < 1) {
      throw new IllegalArgumentException("yeolmu.demo-seed.product-count는 1 이상이어야 합니다.");
    }
    if (sellerCount < 1) {
      throw new IllegalArgumentException("yeolmu.demo-seed.seller-count는 1 이상이어야 합니다.");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("yeolmu.demo-seed.batch-size는 1 이상이어야 합니다.");
    }
    if (!RUN_KEY_PATTERN.matcher(runKey).matches()) {
      throw new IllegalArgumentException(
          "yeolmu.demo-seed.run-key는 영문, 숫자, 하이픈만 사용해 40자 이하로 입력해야 합니다.");
    }
    return new SeedOptions(enabled, productCount, sellerCount, batchSize, runKey);
  }

  private int intProperty(String key, int defaultValue) {
    Integer value = environment.getProperty(key, Integer.class);
    return value == null ? defaultValue : value;
  }

  private String createRunKey() {
    return RUN_KEY_FORMATTER.format(clock.instant());
  }

  private String productMarker(String runKey) {
    return DEMO_PRODUCT_TITLE_PREFIX + " " + runKey;
  }

  private String productTitle(String productMarker, int sequence) {
    return "%s %05d %s".formatted(productMarker, sequence, productName(sequence));
  }

  private String productDescription(int sequence) {
    return "프론트 데모 확인용 상품입니다. 상태는 양호하고 직거래 또는 택배 거래가 가능합니다. keyword=" + productName(sequence);
  }

  private int productPrice(int sequence) {
    return 1_000 + Math.floorMod(sequence * 137, 1_500_000);
  }

  private String productName(int sequence) {
    return PRODUCT_NAMES[Math.floorMod(sequence - 1, PRODUCT_NAMES.length)];
  }

  private String sellerEmailPrefix(String runKey) {
    return DEMO_SELLER_EMAIL_PREFIX + runKey + "-";
  }

  private String sellerEmail(String runKey, int sequence) {
    return sellerEmailPrefix(runKey) + "%04d".formatted(sequence) + DEMO_EMAIL_DOMAIN;
  }

  private String sellerNickname(int sequence) {
    return "데모판매자%04d".formatted(sequence);
  }

  private LocalDateTime createdAt(LocalDateTime baseTime, int sequence) {
    return baseTime.minusMinutes(sequence % 525_600L).minusSeconds(sequence % 60L);
  }

  private LocalDateTime nowUtc() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private String escapeLikePattern(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private record SeedOptions(
      boolean enabled, int productCount, int sellerCount, int batchSize, String runKey) {}

  @FunctionalInterface
  private interface SequencePreparedStatementBinder {
    void bind(PreparedStatement preparedStatement, int sequence) throws SQLException;
  }
}
