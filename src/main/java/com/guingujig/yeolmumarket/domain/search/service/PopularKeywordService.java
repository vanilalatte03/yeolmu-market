package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import com.guingujig.yeolmumarket.domain.search.dto.PopularKeywordItemResponse;
import com.guingujig.yeolmumarket.domain.search.dto.PopularKeywordsResponse;
import com.guingujig.yeolmumarket.domain.search.repository.PopularKeywordRepository;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PopularKeywordService {

  private final PopularKeywordRepository popularKeywordRepository;
  private final YeolmuProperties yeolmuProperties;

  /**
   * 상품 검색 요청의 키워드를 인기 검색어 집계에 반영한다.
   *
   * <p>집계용 정규화는 앞뒤 공백 제거만 수행하며, DB LIKE 검색용 escape와 독립적으로 유지한다.
   */
  public void recordSearchKeyword(String keyword) {
    String normalizedKeyword = normalizeKeywordForAggregation(keyword);
    if (normalizedKeyword == null) {
      return;
    }
    popularKeywordRepository.incrementSearchCount(normalizedKeyword);
  }

  public PopularKeywordsResponse getPopularKeywords(Integer limit) {
    int resolvedLimit = resolveLimit(limit);
    List<PopularKeyword> popularKeywords = findTopKeywords(resolvedLimit);

    List<PopularKeywordItemResponse> responses = new ArrayList<>();
    for (int index = 0; index < popularKeywords.size(); index++) {
      PopularKeyword popularKeyword = popularKeywords.get(index);
      responses.add(
          new PopularKeywordItemResponse(
              index + 1, popularKeyword.keyword(), popularKeyword.searchCount()));
    }
    return new PopularKeywordsResponse(responses);
  }

  private List<PopularKeyword> findTopKeywords(int limit) {
    try {
      return popularKeywordRepository.findTopKeywords(limit);
    } catch (DataAccessException exception) {
      throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }
  }

  private int resolveLimit(Integer limit) {
    YeolmuProperties.PopularKeywords properties = yeolmuProperties.search().popularKeywords();
    if (limit == null) {
      return properties.defaultLimit();
    }
    if (limit < 1 || limit > properties.maxLimit()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_FAILED, "limit은 1 이상 " + properties.maxLimit() + " 이하이어야 합니다.");
    }
    return limit;
  }

  private String normalizeKeywordForAggregation(String keyword) {
    if (keyword == null) {
      return null;
    }

    String normalizedKeyword = keyword.trim();
    if (normalizedKeyword.isBlank()) {
      return null;
    }
    return normalizedKeyword;
  }
}
