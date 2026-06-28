package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductThumbnailQueryService {

  private final ProductImageRepository productImageRepository;

  @Transactional(readOnly = true)
  public Map<Long, String> getThumbnailUrls(Collection<Long> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return Map.of();
    }

    return productImageRepository.findThumbnailUrlsByProductIds(productIds).stream()
        .collect(
            Collectors.toMap(
                ProductImageRepository.ProductThumbnailUrl::getProductId,
                ProductImageRepository.ProductThumbnailUrl::getUrl,
                (first, second) -> first));
  }
}
