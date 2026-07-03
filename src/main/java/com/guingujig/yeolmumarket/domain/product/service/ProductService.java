package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.dto.AdminHiddenProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemProjection;
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
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final ProductChangeEventPublisher productChangeEventPublisher;
  private final YeolmuProperties yeolmuProperties;

  /**
   * 인증된 회원을 판매자로 지정해 상품을 등록한다.
   *
   * <p>P1부터 상품 카테고리는 필수이며, 신규 상품의 기본 상태는 판매 중이다.
   */
  @Transactional
  public CreateProductResponse createProduct(CreateProductCommand command) {
    Product product =
        Product.create(
            command.seller(),
            command.request().title(),
            command.request().description(),
            command.request().price(),
            command.category());
    Product savedProduct = productRepository.save(product);
    productChangeEventPublisher.publishSearchIndexChanged(
        savedProduct.getId(), savedProduct.getStatus());
    return CreateProductResponse.from(savedProduct);
  }

  /**
   * 일반 사용자에게 공개할 수 있는 상품 목록을 조회한다.
   *
   * <p>숨김 상품과 삭제 상품은 공개 목록에 노출하지 않으며, 상품에 대표 이미지가 있으면 {@code thumbnailUrl}에 반영한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<ProductListItemProjection> getPublicListItems(ProductListQuery query) {
    validatePagination(query.page(), query.size());
    ProductStatus queryStatus = resolveStatus(query.status());
    validatePublicListStatus(queryStatus);

    Page<ProductListItemProjection> products =
        productRepository.findPublicListItemsByStatus(
            queryStatus, PageRequest.of(query.page(), query.size(), resolveSort(query.sort())));
    return PageResponse.from(products);
  }

  /**
   * 일반 사용자에게 공개할 수 있는 상품 상세 정보를 조회한다.
   *
   * <p>존재하지 않거나, 숨김 처리되었거나, 삭제된 상품은 모두 {@code PRODUCT_NOT_FOUND}로 응답한다.
   */
  @Transactional(readOnly = true)
  public Product getPublicProduct(Long productId) {
    return productRepository
        .findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(productId, ProductStatus.DELETED)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
  }

  /**
   * 주문 생성 Facade가 사용할 상품 예약 계약이다.
   *
   * <p>공개 상품만 조회하고, 판매자 본인 주문과 판매 중이 아닌 상품을 검증한 뒤 RESERVED로 전이한다. Product.@Version flush 경합은 {@code
   * ORDER_ALREADY_EXISTS}로 변환한다.
   */
  @Transactional
  public Product reservePublicProductForOrder(Long buyerId, Long productId) {
    Product product =
        productRepository
            .findWithSellerById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    product.reserveForOrder(buyerId);

    try {
      productRepository.flush();
    } catch (OptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.ORDER_ALREADY_EXISTS);
    }

    productChangeEventPublisher.publishSearchIndexAndDisplayChanged(
        product.getId(), ProductStatus.ON_SALE, ProductStatus.RESERVED);
    return product;
  }

  /**
   * 채팅방 생성 Facade가 사용할 상품 조회 계약이다.
   *
   * <p>상품과 판매자를 pessimistic lock으로 조회하고, 요청자가 해당 상품으로 채팅방을 만들 수 있는지까지 검증한다.
   */
  @Transactional
  public Product getChatCreatableProductForUpdate(Long productId, Long buyerId) {
    Product product =
        productRepository
            .findWithSellerByIdForUpdate(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    product.validateChatCreatableBy(buyerId);
    return product;
  }

  /**
   * 카테고리 상품 목록 Facade가 사용할 공개 상품 조회 계약이다.
   *
   * <p>카테고리 존재 검증과 썸네일 조합은 호출자가 담당하고, 이 메서드는 숨김/삭제 상품을 제외한 상품 Page만 반환한다.
   */
  @Transactional(readOnly = true)
  public Page<Product> getPublicCategoryProducts(CategoryProductsQuery query) {
    validatePagination(query.page(), query.size());
    return productRepository.findByCategoryIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
        query.categoryId(),
        ProductStatus.DELETED,
        PageRequest.of(query.page(), query.size(), resolveSort(query.sort())));
  }

  /** 카테고리 상품 조회 Facade가 기존 에러 우선순위를 유지하기 위해 먼저 호출하는 페이지 검증 계약이다. */
  public void validateCategoryProductsPagination(CategoryProductsQuery query) {
    validatePagination(query.page(), query.size());
  }

  @Transactional(readOnly = true)
  public List<ProductImage> getProductImages(Long productId) {
    return productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(productId);
  }

  /**
   * 특정 판매자의 공개 상품 목록을 조회한다.
   *
   * <p>비회원도 호출할 수 있으므로 숨김 상품과 삭제 상품은 항상 제외하고, 존재하지 않는 판매자 ID는 회원 없음으로 응답한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<UserProductListItemResponse> getPublicSellerProducts(
      SellerProductsQuery query) {
    validatePagination(query.page(), query.size());

    Pageable pageable = PageRequest.of(query.page(), query.size(), resolveSort(null));
    Page<Product> products = findVisibleSellerProducts(query.sellerId(), query.status(), pageable);

    return PageResponse.from(products.map(UserProductListItemResponse::from));
  }

  /**
   * 로그인 사용자가 본인 판매 상품을 관리 목적으로 조회한다.
   *
   * <p>숨김 상품도 포함하지만, 상태 필터가 없으면 삭제 상품은 제외한다. 삭제 상태를 명시하면 삭제 상품만 조회할 수 있다.
   */
  @Transactional(readOnly = true)
  public PageResponse<UserProductListItemResponse> getMyProducts(SellerProductsQuery query) {
    validatePagination(query.page(), query.size());

    Pageable pageable = PageRequest.of(query.page(), query.size(), resolveSort(null));
    Page<Product> products = findMyProducts(query.sellerId(), query.status(), pageable);

    return PageResponse.from(products.map(UserProductListItemResponse::from));
  }

  /**
   * 상품 판매자 본인만 제목, 설명, 가격, 카테고리를 변경할 수 있다.
   *
   * <p>삭제된 상품은 존재하지 않는 상품처럼 처리한다.
   */
  @Transactional
  public UpdateProductResponse updateProduct(UpdateProductCommand command) {
    UpdateProductRequest request = command.request();
    Product product = command.product();

    boolean searchIndexChanged = isSearchIndexChanged(request);
    boolean productDisplayChanged = isProductDisplayChanged(request);
    product.updateInfo(request.title(), request.description(), request.price());
    if (command.category() != null) {
      product.changeCategory(command.category());
    }
    productRepository.flush();
    if (searchIndexChanged) {
      productChangeEventPublisher.publishSearchIndexChanged(product.getId(), product.getStatus());
    }
    if (productDisplayChanged) {
      productChangeEventPublisher.publishDisplayChanged(product.getId());
    }
    return UpdateProductResponse.from(product);
  }

  Product getUpdateTarget(Long sellerId, Long productId, UpdateProductRequest request) {
    validateUpdatableValue(request);
    Product product = getExistingProduct(productId);
    product.validateSeller(sellerId);
    return product;
  }

  /**
   * 상품 판매자 본인만 상품을 소프트 삭제할 수 있다.
   *
   * <p>P0에서는 예약 중 상태를 거래 진행 중으로 보고 삭제를 거부한다.
   */
  @Transactional
  public DeleteProductResponse deleteProduct(Long sellerId, Long productId) {
    Product product = getExistingProduct(productId);
    ProductStatus previousStatus = product.getStatus();

    product.deleteBySeller(sellerId, LocalDateTime.now(ZoneOffset.UTC));

    productChangeEventPublisher.publishSearchIndexAndDisplayChanged(
        product.getId(), previousStatus);
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

    boolean hiddenChanged = product.isHidden() != request.hidden();
    product.changeHidden(request.hidden());
    if (hiddenChanged) {
      productChangeEventPublisher.publishSearchIndexAndDisplayChanged(
          product.getId(), product.getStatus());
    }
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

  private void validateUpdatableValue(UpdateProductRequest request) {
    if (!request.isUpdatableValuePresent()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "수정할 값은 하나 이상이어야 합니다.");
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

  void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > yeolmuProperties.pagination().maxPageSize()) {
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

  private boolean isSearchIndexChanged(UpdateProductRequest request) {
    return request.title() != null || request.description() != null || request.price() != null;
  }

  private boolean isProductDisplayChanged(UpdateProductRequest request) {
    return request.title() != null || request.price() != null;
  }
}
