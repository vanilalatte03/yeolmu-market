package com.guingujig.yeolmumarket.domain.category.service;

import com.guingujig.yeolmumarket.domain.category.dto.CategoryProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.service.CategoryProductsQuery;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.domain.product.service.ProductThumbnailQueryService;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryFacade {

  private final CategoryService categoryService;
  private final ProductService productService;
  private final ProductThumbnailQueryService productThumbnailQueryService;

  /**
   * 특정 카테고리에 속한 공개 상품 목록을 조회한다.
   *
   * <p>카테고리 존재 확인, 공개 상품 Page 조회, 대표 이미지 조합을 각 도메인 Service 계약으로 분리해 조합한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<CategoryProductListItemResponse> getCategoryProducts(
      Long categoryId, int page, int size, String sort) {
    categoryService.validateCategoryExists(categoryId);
    Page<Product> products =
        productService.getPublicCategoryProducts(
            new CategoryProductsQuery(categoryId, page, size, sort));
    Map<Long, String> thumbnailUrls =
        productThumbnailQueryService.getThumbnailUrls(productIds(products));

    return PageResponse.from(
        products.map(
            product ->
                CategoryProductListItemResponse.from(
                    product, thumbnailUrl(product, thumbnailUrls))));
  }

  private List<Long> productIds(Page<Product> products) {
    return products.getContent().stream().map(Product::getId).toList();
  }

  private String thumbnailUrl(Product product, Map<Long, String> thumbnailUrls) {
    return thumbnailUrls.get(product.getId());
  }
}
