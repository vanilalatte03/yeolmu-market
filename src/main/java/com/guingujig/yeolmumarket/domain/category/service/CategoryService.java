package com.guingujig.yeolmumarket.domain.category.service;

import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.dto.DeleteCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.dto.GetCategoriesResponse;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;

  /** 상품 등록과 탐색에 사용할 전체 카테고리 목록을 ID 오름차순으로 조회한다. */
  @Transactional(readOnly = true)
  public GetCategoriesResponse getCategories() {
    return GetCategoriesResponse.from(categoryRepository.findAll(DEFAULT_SORT));
  }

  /**
   * 관리자 카테고리를 생성한다.
   *
   * <p>이미 같은 이름의 카테고리가 있으면 {@code CATEGORY_NAME_ALREADY_EXISTS}로 실패한다.
   */
  @Transactional
  public CreateCategoryResponse createCategory(CreateCategoryRequest request) {
    validateNameDoesNotExist(request.name());

    try {
      Category category = categoryRepository.saveAndFlush(Category.create(request.name()));
      return CreateCategoryResponse.from(category);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
    }
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

    try {
      category.updateName(request.name());
      categoryRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
    }
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
    validateCategoryIsNotInUse(categoryId);

    categoryRepository.delete(category);
    return DeleteCategoryResponse.success();
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

  private void validateCategoryIsNotInUse(Long categoryId) {
    if (productRepository.existsByCategoryId(categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
    }
  }
}
