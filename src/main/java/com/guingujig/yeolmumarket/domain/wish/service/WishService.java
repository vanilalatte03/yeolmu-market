package com.guingujig.yeolmumarket.domain.wish.service;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductThumbnailQueryService;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.wish.dto.WishListItemResponse;
import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishService {

  private final WishRepository wishRepository;
  private final ProductThumbnailQueryService productThumbnailQueryService;
  private final YeolmuProperties yeolmuProperties;

  @Transactional
  public WishResponse createWish(User user, Product product) {
    Long userId = user.getId();
    Long productId = product.getId();
    if (wishRepository.existsByUserIdAndProductId(userId, productId)) {
      throw new BusinessException(ErrorCode.WISH_ALREADY_EXISTS);
    }

    wishRepository.save(Wish.create(user, product));

    return new WishResponse(productId, true, wishRepository.countByProductId(productId));
  }

  @Transactional
  public WishResponse deleteWish(User user, Product product) {
    Long productId = product.getId();
    Wish wish =
        wishRepository
            .findByUserAndProduct(user, product)
            .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));

    wishRepository.delete(wish);
    return new WishResponse(productId, false, wishRepository.countByProductId(productId));
  }

  @Transactional(readOnly = true)
  public PageResponse<WishListItemResponse> getMyWishes(Long userId, int page, int size) {
    validatePagination(page, size);

    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    Page<Wish> wishes =
        wishRepository.findPublicWishesByUserId(userId, ProductStatus.DELETED, pageable);
    List<Long> productIds =
        wishes.getContent().stream().map(wish -> wish.getProduct().getId()).toList();
    Map<Long, String> thumbnailUrls = productThumbnailQueryService.getThumbnailUrls(productIds);

    return PageResponse.from(
        wishes.map(
            wish -> WishListItemResponse.from(wish, thumbnailUrls.get(wish.getProduct().getId()))));
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > yeolmuProperties.pagination().maxPageSize()) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }
}
