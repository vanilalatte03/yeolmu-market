package com.guingujig.yeolmumarket.domain.wish.service;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.service.UserService;
import com.guingujig.yeolmumarket.domain.wish.dto.WishListItemResponse;
import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishFacade {

  private final UserService userService;
  private final ProductService productService;
  private final WishService wishService;

  @Transactional
  public WishResponse createWish(Long userId, Long productId) {
    User user = userService.getExistingUser(userId);
    Product product = productService.getPublicProduct(productId);
    return wishService.createWish(user, product);
  }

  @Transactional
  public WishResponse deleteWish(Long userId, Long productId) {
    User user = userService.getExistingUser(userId);
    Product product = productService.getPublicProduct(productId);
    return wishService.deleteWish(user, product);
  }

  @Transactional(readOnly = true)
  public PageResponse<WishListItemResponse> getMyWishes(Long userId, int page, int size) {
    userService.validateUserExists(userId);
    return wishService.getMyWishes(userId, page, size);
  }
}
