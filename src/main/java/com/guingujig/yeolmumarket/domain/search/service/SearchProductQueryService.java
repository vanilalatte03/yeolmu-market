package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchProductQueryService {

  private final ProductRepository productRepository;

  public PageResponse<Long> searchProductIds(SearchProductCondition condition) {
    Page<Long> productIds =
        productRepository.searchPublicProductIds(
            condition.keyword(),
            condition.minPrice(),
            condition.maxPrice(),
            condition.status(),
            condition.toPageRequest());

    return PageResponse.from(productIds);
  }
}
