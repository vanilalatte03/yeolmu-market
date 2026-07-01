package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.service.CategoryService;
import com.guingujig.yeolmumarket.domain.product.dto.AdminHiddenProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductDetailResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemProjection;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UserProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.review.service.ReviewRatingQueryService;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.service.UserService;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import com.guingujig.yeolmumarket.domain.wish.service.ProductWishSummaryService;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductFacade {

  private final ProductService productService;
  private final UserService userService;
  private final CategoryService categoryService;
  private final ProductWishSummaryService productWishSummaryService;
  private final ReviewRatingQueryService reviewRatingQueryService;

  @Transactional(readOnly = true)
  public PageResponse<ProductListItemResponse> getProducts(
      ProductListQuery query, Long authenticatedUserId) {
    PageResponse<ProductListItemProjection> products = productService.getPublicListItems(query);
    Map<Long, ProductWishSummary> wishSummaries =
        productWishSummaryService.getSummaries(productIds(products.content()), authenticatedUserId);

    return new PageResponse<>(
        products.content().stream()
            .map(product -> toProductListItemResponse(product, wishSummaries))
            .toList(),
        products.page(),
        products.size(),
        products.totalElements(),
        products.totalPages(),
        products.hasNext());
  }

  @Transactional(readOnly = true)
  public ProductDetailResponse getProduct(Long productId, Long authenticatedUserId) {
    Product product = productService.getPublicProduct(productId);
    ProductWishSummary wishSummary =
        productWishSummaryService.getSummary(productId, authenticatedUserId);
    List<ProductImage> images = productService.getProductImages(productId);

    return ProductDetailResponse.from(
        product,
        wishSummary,
        images,
        reviewRatingQueryService.getSummary(product.getSeller().getId()));
  }

  @Transactional
  public CreateProductResponse createProduct(Long sellerId, CreateProductRequest request) {
    User seller = userService.getExistingUser(sellerId);
    Category category = categoryService.getExistingCategory(request.categoryId());
    return productService.createProduct(new CreateProductCommand(seller, category, request));
  }

  @Transactional(readOnly = true)
  public PageResponse<UserProductListItemResponse> getPublicSellerProducts(
      SellerProductsQuery query) {
    productService.validatePagination(query.page(), query.size());
    userService.validateUserExists(query.sellerId());
    return productService.getPublicSellerProducts(query);
  }

  @Transactional(readOnly = true)
  public PageResponse<UserProductListItemResponse> getMyProducts(SellerProductsQuery query) {
    productService.validatePagination(query.page(), query.size());
    userService.validateUserExists(query.sellerId());
    return productService.getMyProducts(query);
  }

  @Transactional
  public UpdateProductResponse updateProduct(
      Long sellerId, Long productId, UpdateProductRequest request) {
    Product product = productService.getUpdateTarget(sellerId, productId, request);
    Category category = resolveCategory(request);
    return productService.updateProduct(new UpdateProductCommand(product, request, category));
  }

  @Transactional
  public DeleteProductResponse deleteProduct(Long sellerId, Long productId) {
    return productService.deleteProduct(sellerId, productId);
  }

  @Transactional
  public UpdateProductHiddenStatusResponse updateProductHiddenStatus(
      Long productId, UpdateProductHiddenStatusRequest request) {
    return productService.updateProductHiddenStatus(productId, request);
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminHiddenProductResponse> getHiddenProducts(int page, int size) {
    return productService.getHiddenProducts(page, size);
  }

  private Category resolveCategory(UpdateProductRequest request) {
    if (request.categoryId() == null) {
      return null;
    }
    return categoryService.getExistingCategory(request.categoryId());
  }

  private ProductListItemResponse toProductListItemResponse(
      ProductListItemProjection product, Map<Long, ProductWishSummary> wishSummaries) {
    Long productId = product.productId();
    ProductWishSummary wishSummary =
        wishSummaries.getOrDefault(productId, ProductWishSummary.empty(productId));

    return ProductListItemResponse.from(product, wishSummary);
  }

  private List<Long> productIds(List<ProductListItemProjection> products) {
    return products.stream().map(ProductListItemProjection::productId).toList();
  }
}
