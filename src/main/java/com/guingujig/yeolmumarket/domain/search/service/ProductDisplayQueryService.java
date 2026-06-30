package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.product.service.ProductThumbnailQueryService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductDisplayQueryService {

  private static final Logger log = LoggerFactory.getLogger(ProductDisplayQueryService.class);

  private final ProductRepository productRepository;
  private final ProductThumbnailQueryService productThumbnailQueryService;
  private final CacheManager cacheManager;

  @Transactional(readOnly = true)
  public List<SearchProductDisplay> getDisplays(List<Long> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return List.of();
    }

    List<Long> distinctProductIds =
        productIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctProductIds.isEmpty()) {
      return List.of();
    }

    Cache cache = cacheManager.getCache(SearchCacheNames.PRODUCT_DISPLAY_V2);
    Map<Long, SearchProductDisplay> displays = new HashMap<>();
    List<Long> missingProductIds = new ArrayList<>();
    boolean cacheAvailable = cache != null;
    for (Long productId : distinctProductIds) {
      SearchProductDisplay cachedDisplay = null;
      if (cacheAvailable) {
        try {
          cachedDisplay = cache.get(productId, SearchProductDisplay.class);
        } catch (RuntimeException exception) {
          cacheAvailable = false;
          log.warn("상품 표시 캐시 조회에 실패해 DB에서 조회합니다. productId={}", productId, exception);
        }
      }
      if (cachedDisplay == null) {
        missingProductIds.add(productId);
      } else {
        displays.put(productId, cachedDisplay);
      }
    }

    Map<Long, SearchProductDisplay> loadedDisplays = loadDisplays(missingProductIds);
    displays.putAll(loadedDisplays);
    if (cacheAvailable) {
      putDisplays(cache, loadedDisplays.values());
    }

    return productIds.stream().map(displays::get).filter(Objects::nonNull).toList();
  }

  private Map<Long, SearchProductDisplay> loadDisplays(Collection<Long> productIds) {
    if (productIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, String> thumbnailUrls = productThumbnailQueryService.getThumbnailUrls(productIds);
    return productRepository.findSearchDisplaysByIds(productIds, ProductStatus.DELETED).stream()
        .map(projection -> toSearchProductDisplay(projection, thumbnailUrls))
        .collect(
            Collectors.toMap(
                SearchProductDisplay::productId, display -> display, (first, second) -> first));
  }

  private SearchProductDisplay toSearchProductDisplay(
      ProductRepository.ProductSearchDisplayProjection projection,
      Map<Long, String> thumbnailUrls) {
    return new SearchProductDisplay(
        projection.getProductId(),
        projection.getTitle(),
        projection.getPrice(),
        projection.getStatus(),
        thumbnailUrls.get(projection.getProductId()),
        projection.getSellerId(),
        projection.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  private void putDisplays(Cache cache, Collection<SearchProductDisplay> displays) {
    for (SearchProductDisplay display : displays) {
      try {
        cache.put(display.productId(), display);
      } catch (RuntimeException exception) {
        log.warn("상품 표시 캐시 저장에 실패했습니다. productId={}", display.productId(), exception);
        return;
      }
    }
  }
}
