package com.guingujig.yeolmumarket.domain.wish.service;

import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductWishSummaryService {

  private final WishRepository wishRepository;

  @Transactional(readOnly = true)
  public Map<Long, ProductWishSummary> getSummaries(
      Collection<Long> productIds, Long authenticatedUserId) {
    List<Long> distinctProductIds =
        productIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctProductIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, Long> wishCounts =
        wishRepository.countByProductIds(distinctProductIds).stream()
            .collect(
                Collectors.toMap(
                    WishRepository.ProductWishCount::getProductId,
                    WishRepository.ProductWishCount::getWishCount));
    Set<Long> wishedProductIds = findWishedProductIds(authenticatedUserId, distinctProductIds);

    return distinctProductIds.stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                productId ->
                    new ProductWishSummary(
                        productId,
                        wishCounts.getOrDefault(productId, 0L),
                        wishedProductIds.contains(productId))));
  }

  @Transactional(readOnly = true)
  public ProductWishSummary getSummary(Long productId, Long authenticatedUserId) {
    return getSummaries(List.of(productId), authenticatedUserId)
        .getOrDefault(productId, ProductWishSummary.empty(productId));
  }

  private Set<Long> findWishedProductIds(Long authenticatedUserId, List<Long> productIds) {
    if (authenticatedUserId == null) {
      return Set.of();
    }
    return wishRepository.findWishedProductIdsByUserId(authenticatedUserId, productIds);
  }
}
