package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.dto.AdminHiddenProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductDetailResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UserProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.review.service.ReviewRatingQueryService;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import com.guingujig.yeolmumarket.domain.wish.service.ProductWishSummaryService;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private static final int MAX_PAGE_SIZE = 100;

  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final ProductThumbnailQueryService productThumbnailQueryService;
  private final ProductWishSummaryService productWishSummaryService;
  private final ReviewRatingQueryService reviewRatingQueryService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 인증된 회원을 판매자로 지정해 상품을 등록한다.
   *
   * <p>P1부터 상품 카테고리는 필수이며, 신규 상품의 기본 상태는 판매 중이다.
   */
  @Transactional
  public CreateProductResponse createProduct(Long sellerId, CreateProductRequest request) {
    User seller =
        userRepository
            .findById(sellerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Category category = getCategory(request.categoryId());

    Product product =
        Product.create(seller, request.title(), request.description(), request.price(), category);
    Product savedProduct = productRepository.save(product);
    publishProductSearchCacheEviction();
    return CreateProductResponse.from(savedProduct);
  }

  /**
   * 일반 사용자에게 공개할 수 있는 상품 목록을 조회한다.
   *
   * <p>숨김 상품과 삭제 상품은 공개 목록에 노출하지 않으며, 상품에 대표 이미지가 있으면 {@code thumbnailUrl}에 반영한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<ProductListItemResponse> getProducts(
      int page, int size, ProductStatus status, String sort, Long authenticatedUserId) {
    validatePagination(page, size);
    ProductStatus queryStatus = resolveStatus(status);
    validatePublicListStatus(queryStatus);

    Page<Product> products =
        productRepository.findByHiddenFalseAndDeletedAtIsNullAndStatus(
            queryStatus, PageRequest.of(page, size, resolveSort(sort)));
    List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
    Map<Long, ProductWishSummary> wishSummaries =
        productWishSummaryService.getSummaries(productIds, authenticatedUserId);
    Map<Long, String> thumbnailUrls = productThumbnailQueryService.getThumbnailUrls(productIds);

    Page<ProductListItemResponse> productResponses =
        products.map(product -> toProductListItemResponse(product, wishSummaries, thumbnailUrls));

    return PageResponse.from(productResponses);
  }

  private ProductListItemResponse toProductListItemResponse(
      Product product,
      Map<Long, ProductWishSummary> wishSummaries,
      Map<Long, String> thumbnailUrls) {
    Long productId = product.getId();
    ProductWishSummary wishSummary =
        wishSummaries.getOrDefault(productId, ProductWishSummary.empty(productId));
    String thumbnailUrl = thumbnailUrls.get(productId);

    return ProductListItemResponse.from(product, wishSummary, thumbnailUrl);
  }

  /**
   * 일반 사용자에게 공개할 수 있는 상품 상세 정보를 조회한다.
   *
   * <p>존재하지 않거나, 숨김 처리되었거나, 삭제된 상품은 모두 {@code PRODUCT_NOT_FOUND}로 응답한다.
   */
  @Transactional(readOnly = true)
  public ProductDetailResponse getProduct(Long productId, Long authenticatedUserId) {
    Product product =
        productRepository
            .findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(productId, ProductStatus.DELETED)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    ProductWishSummary wishSummary =
        productWishSummaryService.getSummary(productId, authenticatedUserId);
    List<ProductImage> images =
        productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(productId);

    return ProductDetailResponse.from(
        product,
        wishSummary,
        images,
        reviewRatingQueryService.getSummary(product.getSeller().getId()));
  }

  /**
   * 특정 판매자의 공개 상품 목록을 조회한다.
   *
   * <p>비회원도 호출할 수 있으므로 숨김 상품과 삭제 상품은 항상 제외하고, 존재하지 않는 판매자 ID는 회원 없음으로 응답한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<UserProductListItemResponse> getPublicSellerProducts(
      Long sellerId, int page, int size, ProductStatus status) {
    validatePagination(page, size);
    validateSellerExists(sellerId);

    Pageable pageable = PageRequest.of(page, size, resolveSort(null));
    Page<Product> products = findVisibleSellerProducts(sellerId, status, pageable);

    return PageResponse.from(products.map(UserProductListItemResponse::from));
  }

  /**
   * 로그인 사용자가 본인 판매 상품을 관리 목적으로 조회한다.
   *
   * <p>숨김 상품도 포함하지만, 상태 필터가 없으면 삭제 상품은 제외한다. 삭제 상태를 명시하면 삭제 상품만 조회할 수 있다.
   */
  @Transactional(readOnly = true)
  public PageResponse<UserProductListItemResponse> getMyProducts(
      Long sellerId, int page, int size, ProductStatus status) {
    validatePagination(page, size);
    validateSellerExists(sellerId);

    Pageable pageable = PageRequest.of(page, size, resolveSort(null));
    Page<Product> products = findMyProducts(sellerId, status, pageable);

    return PageResponse.from(products.map(UserProductListItemResponse::from));
  }

  /**
   * 상품 판매자 본인만 제목, 설명, 가격, 카테고리를 변경할 수 있다.
   *
   * <p>삭제된 상품은 존재하지 않는 상품처럼 처리한다.
   */
  @Transactional
  public UpdateProductResponse updateProduct(
      Long sellerId, Long productId, UpdateProductRequest request) {
    validateUpdatableValue(request);
    Product product = getExistingProduct(productId);
    validateOwner(product, sellerId);

    product.updateInfo(request.title(), request.description(), request.price());
    if (request.categoryId() != null) {
      product.changeCategory(getCategory(request.categoryId()));
    }
    productRepository.flush();
    publishProductSearchCacheEviction();
    return UpdateProductResponse.from(product);
  }

  /**
   * 상품 판매자 본인만 상품을 소프트 삭제할 수 있다.
   *
   * <p>P0에서는 예약 중 상태를 거래 진행 중으로 보고 삭제를 거부한다.
   */
  @Transactional
  public DeleteProductResponse deleteProduct(Long sellerId, Long productId) {
    Product product = getExistingProduct(productId);
    validateOwner(product, sellerId);
    validateDeletable(product);

    product.delete(LocalDateTime.now(ZoneOffset.UTC));
    publishProductSearchCacheEviction();
    return DeleteProductResponse.success();
  }

  /**
   * 관리자가 상품 공개 노출 여부를 변경한다.
   *
   * <p>존재하지 않거나 삭제된 상품은 숨김 상태 변경 대상이 아니며, 상품 거래 상태는 변경하지 않는다.
   */
  @Transactional
  public UpdateProductHiddenStatusResponse updateProductHiddenStatus(
      Long productId, UpdateProductHiddenStatusRequest request) {
    Product product = getExistingProduct(productId);

    product.changeHidden(request.hidden());
    publishProductSearchCacheEviction();
    return UpdateProductHiddenStatusResponse.from(product);
  }

  /**
   * 관리자가 숨김 처리된 상품 목록을 조회한다.
   *
   * <p>삭제된 상품은 운영 관리 목록에서도 제외한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminHiddenProductResponse> getHiddenProducts(int page, int size) {
    validatePagination(page, size);

    Page<Product> products =
        productRepository.findByHiddenTrueAndDeletedAtIsNullAndStatusNot(
            ProductStatus.DELETED, PageRequest.of(page, size, resolveAdminHiddenProductSort()));

    return PageResponse.from(products.map(AdminHiddenProductResponse::from));
  }

  private Product getExistingProduct(Long productId) {
    return productRepository
        .findWithSellerById(productId)
        .filter(product -> !product.isDeleted())
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
  }

  private Category getCategory(Long categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
  }

  private void validateOwner(Product product, Long sellerId) {
    if (!Objects.equals(product.getSeller().getId(), sellerId)) {
      throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
    }
  }

  private void validateUpdatableValue(UpdateProductRequest request) {
    if (!request.isUpdatableValuePresent()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "수정할 값은 하나 이상이어야 합니다.");
    }
  }

  private void validateDeletable(Product product) {
    if (product.hasActiveOrder()) {
      throw new BusinessException(ErrorCode.PRODUCT_HAS_ACTIVE_ORDER);
    }
  }

  private void validateSellerExists(Long sellerId) {
    if (!userRepository.existsById(sellerId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
  }

  private Page<Product> findPublicSellerProducts(
      Long sellerId, ProductStatus status, Pageable pageable) {
    if (status == null) {
      return productRepository.findBySellerIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
          sellerId, ProductStatus.DELETED, pageable);
    }
    return productRepository.findBySellerIdAndHiddenFalseAndDeletedAtIsNullAndStatus(
        sellerId, status, pageable);
  }

  private Page<Product> findVisibleSellerProducts(
      Long sellerId, ProductStatus status, Pageable pageable) {
    if (status == ProductStatus.DELETED) {
      return Page.empty(pageable);
    }
    return findPublicSellerProducts(sellerId, status, pageable);
  }

  private Page<Product> findMyProducts(Long sellerId, ProductStatus status, Pageable pageable) {
    if (status == null) {
      return productRepository.findBySellerIdAndDeletedAtIsNullAndStatusNot(
          sellerId, ProductStatus.DELETED, pageable);
    }
    if (status == ProductStatus.DELETED) {
      return productRepository.findBySellerIdAndStatus(sellerId, status, pageable);
    }
    return productRepository.findBySellerIdAndDeletedAtIsNullAndStatus(sellerId, status, pageable);
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private ProductStatus resolveStatus(ProductStatus status) {
    if (status == null) {
      return ProductStatus.ON_SALE;
    }
    return status;
  }

  private void validatePublicListStatus(ProductStatus status) {
    if (status == ProductStatus.DELETED) {
      throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
    }
  }

  private Sort resolveSort(String sort) {
    if (sort == null || sort.isBlank() || sort.equals("latest")) {
      return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }
    return switch (sort) {
      case "priceAsc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
      case "priceDesc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
      default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 정렬 조건입니다.");
    };
  }

  private Sort resolveAdminHiddenProductSort() {
    return Sort.by(Sort.Order.desc("modifiedAt"), Sort.Order.desc("id"));
  }

  private void publishProductSearchCacheEviction() {
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());
  }
}
