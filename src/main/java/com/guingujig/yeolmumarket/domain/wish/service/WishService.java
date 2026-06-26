package com.guingujig.yeolmumarket.domain.wish.service;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishService {

  private final WishRepository wishRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;

  @Transactional
  public WishResponse createWish(Long userId, Long productId) {
    User user = getUser(userId);
    Product product = getPublicProduct(productId);
    if (wishRepository.existsByUserIdAndProductId(userId, productId)) {
      throw new BusinessException(ErrorCode.WISH_ALREADY_EXISTS);
    }

    try {
      wishRepository.saveAndFlush(Wish.create(user, product));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.WISH_ALREADY_EXISTS);
    }

    return new WishResponse(productId, true, wishRepository.countByProductId(productId));
  }

  @Transactional
  public WishResponse deleteWish(Long userId, Long productId) {
    User user = getUser(userId);
    Product product = getPublicProduct(productId);
    Wish wish =
        wishRepository
            .findByUserAndProduct(user, product)
            .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));

    wishRepository.delete(wish);
    wishRepository.flush();
    return new WishResponse(productId, false, wishRepository.countByProductId(productId));
  }

  private User getUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private Product getPublicProduct(Long productId) {
    return productRepository
        .findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(productId, ProductStatus.DELETED)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
  }
}
