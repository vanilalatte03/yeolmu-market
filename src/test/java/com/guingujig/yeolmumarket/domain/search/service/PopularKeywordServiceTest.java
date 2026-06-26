package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import com.guingujig.yeolmumarket.domain.search.dto.PopularKeywordsResponse;
import com.guingujig.yeolmumarket.domain.search.repository.PopularKeywordRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class PopularKeywordServiceTest {

  @Mock private PopularKeywordRepository popularKeywordRepository;

  private PopularKeywordService popularKeywordService;

  @BeforeEach
  void setUp() {
    popularKeywordService = new PopularKeywordService(popularKeywordRepository);
  }

  @Test
  void 검색어_집계시_앞뒤_공백만_제거한다() {
    popularKeywordService.recordSearchKeyword("  아이패드  미니  ");

    verify(popularKeywordRepository).incrementSearchCount("아이패드  미니");
  }

  @Test
  void 인기_검색어를_rank와_검색횟수로_반환한다() {
    when(popularKeywordRepository.findTopKeywords(2))
        .thenReturn(List.of(new PopularKeyword("아이패드", 3), new PopularKeyword("맥북", 2)));

    PopularKeywordsResponse response = popularKeywordService.getPopularKeywords(2);

    assertThat(response.keywords()).hasSize(2);
    assertThat(response.keywords().get(0).rank()).isEqualTo(1);
    assertThat(response.keywords().get(0).keyword()).isEqualTo("아이패드");
    assertThat(response.keywords().get(0).searchCount()).isEqualTo(3);
    assertThat(response.keywords().get(1).rank()).isEqualTo(2);
  }

  @Test
  void limit이_없으면_기본값_10으로_조회한다() {
    popularKeywordService.getPopularKeywords(null);

    verify(popularKeywordRepository).findTopKeywords(10);
  }

  @Test
  void limit_범위가_잘못되면_VALIDATION_FAILED로_실패한다() {
    assertThatThrownBy(() -> popularKeywordService.getPopularKeywords(51))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void Redis_조회가_실패하면_REDIS_UNAVAILABLE로_실패한다() {
    when(popularKeywordRepository.findTopKeywords(10))
        .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

    assertThatThrownBy(() -> popularKeywordService.getPopularKeywords(null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE));
  }
}
