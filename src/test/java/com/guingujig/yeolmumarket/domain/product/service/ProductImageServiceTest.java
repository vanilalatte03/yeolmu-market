package com.guingujig.yeolmumarket.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductImageResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UploadProductImagesResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.config.LocalProductImageStorageProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.FileSystemUtils;

@SpringBootTest
class ProductImageServiceTest {

  private final ProductImageService productImageService;
  private final ProductImageRepository productImageRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final LocalProductImageStorageProperties storageProperties;

  @Autowired
  ProductImageServiceTest(
      ProductImageService productImageService,
      ProductImageRepository productImageRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      LocalProductImageStorageProperties storageProperties) {
    this.productImageService = productImageService;
    this.productImageRepository = productImageRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.storageProperties = storageProperties;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
    deleteStorageFiles();
  }

  @AfterEach
  void tearDown() {
    deleteAll();
    deleteStorageFiles();
  }

  @Test
  void 상품_이미지를_업로드하면_첫_이미지를_대표_이미지로_저장한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);

    UploadProductImagesResponse response =
        productImageService.uploadImages(
            seller.getId(),
            product.getId(),
            List.of(image("first.png", "image/png"), image("second.jpg", "image/jpeg")));

    assertThat(response.images()).hasSize(2);
    assertThat(response.images().getFirst().thumbnail()).isTrue();
    assertThat(response.images().getFirst().uploadedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(response.images().get(1).thumbnail()).isFalse();
    assertThat(response.images())
        .extracting(image -> image.url().startsWith("/uploads/products/" + product.getId()))
        .containsOnly(true);

    List<ProductImage> images =
        productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId());
    assertThat(images).hasSize(2);
    assertThat(images.getFirst().isThumbnail()).isTrue();
    assertThat(images.get(1).isThumbnail()).isFalse();
  }

  @Test
  void 기존_이미지가_있으면_추가_업로드_이미지는_대표_이미지로_지정하지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);
    productImageService.uploadImages(
        seller.getId(), product.getId(), List.of(image("first.png", "image/png")));

    UploadProductImagesResponse response =
        productImageService.uploadImages(
            seller.getId(), product.getId(), List.of(image("second.png", "image/png")));

    assertThat(response.images()).hasSize(1);
    assertThat(response.images().getFirst().thumbnail()).isFalse();
    assertThat(productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId()))
        .extracting(ProductImage::isThumbnail)
        .containsExactly(true, false);
  }

  @Test
  void 대표_이미지를_삭제하면_남은_이미지_중_가장_먼저_업로드된_이미지를_대표로_지정한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);
    productImageService.uploadImages(
        seller.getId(),
        product.getId(),
        List.of(image("first.png", "image/png"), image("second.png", "image/png")));
    List<ProductImage> savedImages =
        productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId());

    DeleteProductImageResponse response =
        productImageService.deleteImage(
            seller.getId(), product.getId(), savedImages.getFirst().getId());

    assertThat(response.deleted()).isTrue();
    List<ProductImage> remainingImages =
        productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId());
    assertThat(remainingImages).hasSize(1);
    assertThat(remainingImages.getFirst().getId()).isEqualTo(savedImages.get(1).getId());
    assertThat(remainingImages.getFirst().isThumbnail()).isTrue();
  }

  @Test
  void 마지막_이미지를_삭제하면_대표_이미지는_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);
    productImageService.uploadImages(
        seller.getId(), product.getId(), List.of(image("first.png", "image/png")));
    ProductImage image =
        productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId()).getFirst();

    productImageService.deleteImage(seller.getId(), product.getId(), image.getId());

    assertThat(productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId()))
        .isEmpty();
  }

  @Test
  void 판매자가_아닌_사용자가_이미지를_업로드하거나_삭제하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller);
    productImageService.uploadImages(
        seller.getId(), product.getId(), List.of(image("first.png", "image/png")));
    ProductImage image =
        productImageRepository.findByProductIdOrderByCreatedAtAscIdAsc(product.getId()).getFirst();

    assertThatThrownBy(
            () ->
                productImageService.uploadImages(
                    other.getId(), product.getId(), List.of(image("other.png", "image/png"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
    assertThatThrownBy(
            () -> productImageService.deleteImage(other.getId(), product.getId(), image.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
    assertThatThrownBy(
            () -> productImageService.deleteImage(other.getId(), product.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_상품이나_이미지는_예외로_처리한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);

    assertThatThrownBy(
            () ->
                productImageService.uploadImages(
                    seller.getId(), Long.MAX_VALUE, List.of(image("first.png", "image/png"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    assertThatThrownBy(
            () -> productImageService.deleteImage(seller.getId(), product.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_NOT_FOUND));
  }

  @Test
  void 지원하지_않는_파일_형식은_예외로_처리한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);

    assertThatThrownBy(
            () ->
                productImageService.uploadImages(
                    seller.getId(), product.getId(), List.of(image("memo.txt", "text/plain"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE));
  }

  @Test
  void 파일_크기가_제한을_초과하면_예외로_처리한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);
    byte[] oversizedContent =
        new byte[Math.toIntExact(storageProperties.maxFileSize().toBytes() + 1)];

    assertThatThrownBy(
            () ->
                productImageService.uploadImages(
                    seller.getId(),
                    product.getId(),
                    List.of(
                        new MockMultipartFile("images", "big.png", "image/png", oversizedContent))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED));
  }

  private void deleteAll() {
    productImageRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private void deleteStorageFiles() {
    try {
      FileSystemUtils.deleteRecursively(storageProperties.rootPath());
    } catch (IOException exception) {
      throw new IllegalStateException("테스트 이미지 파일 정리에 실패했습니다.", exception);
    }
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스 조금 있습니다.", 450000);
  }

  private MockMultipartFile image(String filename, String contentType) {
    return new MockMultipartFile(
        "images", filename, contentType, "image-content".getBytes(StandardCharsets.UTF_8));
  }
}
