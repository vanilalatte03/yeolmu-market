package com.guingujig.yeolmumarket.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceDataIntegrityTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private ProductRepository productRepository;

  private CategoryService categoryService;

  @BeforeEach
  void setUp() {
    categoryService = new CategoryService(categoryRepository, productRepository);
  }

  @Test
  void 카테고리_생성_중_DB_제약_위반이_발생하면_중복명_예외로_변환한다() {
    when(categoryRepository.saveAndFlush(any(Category.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate category name"));

    assertThatThrownBy(() -> categoryService.createCategory(new CreateCategoryRequest("디지털기기")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS));
  }

  @Test
  void 카테고리_수정_중_DB_제약_위반이_발생하면_중복명_예외로_변환한다() {
    Long categoryId = 1L;
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(Category.create("가구")));
    when(categoryRepository.existsByNameAndIdNot("디지털기기", categoryId)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("duplicate category name"))
        .when(categoryRepository)
        .flush();

    assertThatThrownBy(
            () -> categoryService.updateCategory(categoryId, new UpdateCategoryRequest("디지털기기")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS));
  }
}
