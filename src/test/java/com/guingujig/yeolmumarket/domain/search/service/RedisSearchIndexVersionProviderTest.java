package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisSearchIndexVersionProviderTest {

  private static final String ON_SALE_VERSION_KEY = "search:index:version:ON_SALE";
  private static final String RESERVED_VERSION_KEY = "search:index:version:RESERVED";

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisSearchIndexVersionProvider versionProvider;

  @BeforeEach
  void setUp() {
    versionProvider = new RedisSearchIndexVersionProvider(stringRedisTemplate);
  }

  @Test
  void 상태별_검색_인덱스_버전을_조회한다() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(ON_SALE_VERSION_KEY)).thenReturn("3");

    String version = versionProvider.currentVersionKey(ProductStatus.ON_SALE);

    assertThat(version).isEqualTo("3");
  }

  @Test
  void 상태별_검색_인덱스_버전이_없으면_초기값을_반환한다() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(ON_SALE_VERSION_KEY)).thenReturn(null);

    String version = versionProvider.currentVersionKey(ProductStatus.ON_SALE);

    assertThat(version).isEqualTo("0");
  }

  @Test
  void 영향받은_상태의_검색_인덱스_버전만_증가시킨다() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    versionProvider.increaseVersions(List.of(ProductStatus.ON_SALE, ProductStatus.RESERVED));

    verify(valueOperations).increment(ON_SALE_VERSION_KEY);
    verify(valueOperations).increment(RESERVED_VERSION_KEY);
  }

  @Test
  void 버전_증가가_실패해도_예외를_전파하지_않는다() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    doThrow(new DataAccessResourceFailureException("redis unavailable"))
        .when(valueOperations)
        .increment(ON_SALE_VERSION_KEY);

    assertThatCode(() -> versionProvider.increaseVersions(List.of(ProductStatus.ON_SALE)))
        .doesNotThrowAnyException();
  }
}
