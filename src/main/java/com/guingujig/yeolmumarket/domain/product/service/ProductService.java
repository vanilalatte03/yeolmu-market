package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductDetailResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private static final int MAX_PAGE_SIZE = 100;

  private final ProductRepository productRepository;
  private final UserRepository userRepository;

  /**
   * 인증된 회원을 판매자로 지정해 상품을 등록한다.
   *
   * <p>P0 상품 등록은 상품명, 설명, 가격만 다루며 신규 상품의 기본 상태는 판매 중이다.
   */
  @Transactional
  public CreateProductResponse createProduct(Long sellerId, CreateProductRequest request) {
    User seller =
        userRepository
            .findById(sellerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Product product =
        Product.create(seller, request.title(), request.description(), request.price());
    Product savedProduct = productRepository.save(product);
    return CreateProductResponse.from(savedProduct);
  }

  /**
   * 일반 사용자에게 공개할 수 있는 상품 목록을 조회한다.
   *
   * <p>숨김 상품과 삭제 상품은 공개 목록에 노출하지 않으며, P0에서는 썸네일을 항상 {@code null}로 반환한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<ProductListItemResponse> getProducts(
      int page, int size, ProductStatus status, String sort) {
    validatePagination(page, size);
    ProductStatus queryStatus = resolveStatus(status);
    validatePublicListStatus(queryStatus);

    Page<ProductListItemResponse> products =
        productRepository
            .findByHiddenFalseAndDeletedAtIsNullAndStatus(
                queryStatus, PageRequest.of(page, size, resolveSort(sort)))
            .map(ProductListItemResponse::from);

    return PageResponse.from(products);
  }

  /**
   * 일반 사용자에게 공개할 수 있는 상품 상세 정보를 조회한다.
   *
   * <p>존재하지 않거나, 숨김 처리되었거나, 삭제된 상품은 모두 {@code PRODUCT_NOT_FOUND}로 응답한다.
   */
  @Transactional(readOnly = true)
  public ProductDetailResponse getProduct(Long productId) {
    Product product =
        productRepository
            .findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(productId, ProductStatus.DELETED)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    return ProductDetailResponse.from(product);
  }

  /**
   * 상품 판매자 본인만 P0 수정 가능 필드인 제목, 설명, 가격을 변경할 수 있다.
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
    productRepository.flush();
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
    return DeleteProductResponse.success();
  }

  private Product getExistingProduct(Long productId) {
    return productRepository
        .findWithSellerById(productId)
        .filter(product -> !product.isDeleted())
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
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
}
