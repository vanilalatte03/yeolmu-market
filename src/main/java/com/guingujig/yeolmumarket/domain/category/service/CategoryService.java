package com.guingujig.yeolmumarket.domain.category.service;

import com.guingujig.yeolmumarket.domain.category.dto.CategoryProductListItemResponse;
import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.dto.DeleteCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.dto.GetCategoriesResponse;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.product.service.ProductThumbnailQueryService;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.TransientPropertyValueException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private static final String LATEST_SORT = "latest";
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final ProductThumbnailQueryService productThumbnailQueryService;
  private final YeolmuProperties yeolmuProperties;

  /** 상품 등록과 탐색에 사용할 전체 카테고리 목록을 ID 오름차순으로 조회한다. */
  @Transactional(readOnly = true)
  public GetCategoriesResponse getCategories() {
    return GetCategoriesResponse.from(categoryRepository.findAll(DEFAULT_SORT));
  }

  /**
   * 특정 카테고리에 속한 공개 상품 목록을 조회한다.
   *
   * <p>존재하는 카테고리만 조회 가능하며, 숨김 상품과 삭제 상품은 결과에서 제외한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<CategoryProductListItemResponse> getCategoryProducts(
      Long categoryId, int page, int size, String sort) {
    validatePagination(page, size);
    validateCategoryExists(categoryId);

    Page<Product> products =
        productRepository.findByCategoryIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
            categoryId,
            ProductStatus.DELETED,
            PageRequest.of(page, size, resolveProductSort(sort)));
    List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
    Map<Long, String> thumbnailUrls = productThumbnailQueryService.getThumbnailUrls(productIds);

    Page<CategoryProductListItemResponse> categoryProducts =
        products.map(product -> toCategoryProductListItemResponse(product, thumbnailUrls));

    return PageResponse.from(categoryProducts);
  }

  private CategoryProductListItemResponse toCategoryProductListItemResponse(
      Product product, Map<Long, String> thumbnailUrls) {
    Long productId = product.getId();
    String thumbnailUrl = thumbnailUrls.get(productId);

    return CategoryProductListItemResponse.from(product, thumbnailUrl);
  }

  /**
   * 관리자 카테고리를 생성한다.
   *
   * <p>이미 같은 이름의 카테고리가 있으면 {@code CATEGORY_NAME_ALREADY_EXISTS}로 실패한다.
   */
  @Transactional
  public CreateCategoryResponse createCategory(CreateCategoryRequest request) {
    validateNameDoesNotExist(request.name());

    Category category = categoryRepository.save(Category.create(request.name()));
    return CreateCategoryResponse.from(category);
  }

  /**
   * 관리자 카테고리명을 변경한다.
   *
   * <p>존재하지 않는 카테고리는 {@code CATEGORY_NOT_FOUND}, 다른 카테고리와 이름이 겹치면 {@code
   * CATEGORY_NAME_ALREADY_EXISTS}로 실패한다.
   */
  @Transactional
  public UpdateCategoryResponse updateCategory(Long categoryId, UpdateCategoryRequest request) {
    Category category = getCategory(categoryId);
    validateNameDoesNotExistForOtherCategory(request.name(), categoryId);

    category.updateName(request.name());
    // updatedAt 응답값을 감사 필드 기준으로 확정한다.
    categoryRepository.flush();
    return UpdateCategoryResponse.from(category);
  }

  /**
   * 관리자 카테고리를 삭제한다.
   *
   * <p>현재 모델에서 해당 카테고리를 참조하는 상품이 있으면 삭제하지 않는다.
   */
  @Transactional
  public DeleteCategoryResponse deleteCategory(Long categoryId) {
    Category category = getCategory(categoryId);

    try {
      categoryRepository.delete(category);
      categoryRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
    } catch (InvalidDataAccessApiUsageException exception) {
      if (isProductCategoryReferenceFailure(exception)) {
        throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
      }
      throw exception;
    }
    return DeleteCategoryResponse.success();
  }

  @Transactional(readOnly = true)
  public Category getExistingCategory(Long categoryId) {
    return getCategory(categoryId);
  }

  private Category getCategory(Long categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
  }

  private void validateNameDoesNotExist(String name) {
    if (categoryRepository.existsByName(name)) {
      throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
    }
  }

  private void validateNameDoesNotExistForOtherCategory(String name, Long categoryId) {
    if (categoryRepository.existsByNameAndIdNot(name, categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
    }
  }

  private boolean isProductCategoryReferenceFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof TransientPropertyValueException exception
          && Product.class.getName().equals(exception.getPropertyOwnerEntityName())
          && "category".equals(exception.getPropertyName())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private void validateCategoryExists(Long categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
    }
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > yeolmuProperties.pagination().maxPageSize()) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private Sort resolveProductSort(String sort) {
    if (sort == null || sort.isBlank() || LATEST_SORT.equals(sort)) {
      return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }
    return switch (sort) {
      case "priceAsc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
      case "priceDesc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
      default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 정렬 조건입니다.");
    };
  }
}
