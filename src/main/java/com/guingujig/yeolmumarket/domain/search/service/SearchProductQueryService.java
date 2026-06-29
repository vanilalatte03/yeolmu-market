package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.product.service.ProductThumbnailQueryService;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchProductQueryService {

  private final ProductRepository productRepository;
  private final ProductThumbnailQueryService productThumbnailQueryService;

  public PageResponse<SearchProductResponse> search(SearchProductCondition condition) {
    Page<Product> products =
        productRepository.searchPublicProducts(
            condition.keyword(),
            condition.minPrice(),
            condition.maxPrice(),
            condition.status(),
            condition.toPageRequest());
    List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
    Map<Long, String> thumbnailUrls = productThumbnailQueryService.getThumbnailUrls(productIds);

    Page<SearchProductResponse> searchProducts =
        products.map(product -> toSearchProductResponse(product, thumbnailUrls));

    return PageResponse.from(searchProducts);
  }

  private SearchProductResponse toSearchProductResponse(
      Product product, Map<Long, String> thumbnailUrls) {
    Long productId = product.getId();
    String thumbnailUrl = thumbnailUrls.get(productId);

    return SearchProductResponse.from(product, thumbnailUrl);
  }
}
