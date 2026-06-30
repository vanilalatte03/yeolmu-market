package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductImageResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UploadProductImagesResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductDisplayChangedEvent;
import com.guingujig.yeolmumarket.global.config.LocalProductImageStorageProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductImageService {

  private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
  private static final Map<String, String> SUPPORTED_IMAGE_EXTENSIONS =
      Map.of(
          "image/jpeg", ".jpg",
          "image/png", ".png",
          "image/gif", ".gif",
          "image/webp", ".webp");

  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final ProductImageStorage productImageStorage;
  private final LocalProductImageStorageProperties storageProperties;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 상품 판매자가 이미지 파일을 업로드한다.
   *
   * <p>첫 업로드 이미지 묶음의 첫 파일을 대표 이미지로 지정하며, DB 트랜잭션이 롤백되면 저장된 로컬 파일도 삭제한다. 상품이 없거나 삭제된 경우 {@code
   * PRODUCT_NOT_FOUND}, 판매자가 아닌 경우 {@code PRODUCT_ACCESS_DENIED}, 지원하지 않는 형식은 {@code
   * UNSUPPORTED_IMAGE_TYPE}, 파일 크기 초과는 {@code FILE_SIZE_EXCEEDED}로 실패한다.
   */
  @Transactional
  public UploadProductImagesResponse uploadImages(
      Long sellerId, Long productId, List<MultipartFile> images) {
    Product product = getExistingProduct(productId);
    validateOwner(product, sellerId);
    validateImagesPresent(images);

    boolean hasThumbnail = productImageRepository.existsByProductId(productId);
    List<String> storedImageUrls = new ArrayList<>();
    registerRollbackCleanup(storedImageUrls);

    List<ProductImage> productImages = new ArrayList<>();
    for (MultipartFile image : images) {
      String extension = validateImage(image);
      StoredProductImage storedImage = productImageStorage.store(productId, image, extension);
      storedImageUrls.add(storedImage.url());
      productImages.add(
          ProductImage.create(
              product, storedImage.url(), !hasThumbnail && productImages.isEmpty()));
    }

    List<ProductImage> savedImages = productImageRepository.saveAll(productImages);
    productImageRepository.flush();
    publishProductDisplayChanged(productId);
    return UploadProductImagesResponse.from(savedImages);
  }

  /**
   * 상품 판매자가 이미지를 삭제한다.
   *
   * <p>대표 이미지를 삭제하면 남은 이미지 중 가장 먼저 업로드된 이미지를 새 대표 이미지로 지정한다. 실제 파일 삭제는 DB 커밋 후 수행해 롤백 시 파일과 DB가
   * 어긋나지 않도록 한다.
   */
  @Transactional
  public DeleteProductImageResponse deleteImage(Long sellerId, Long productId, Long imageId) {
    Product product = getExistingProduct(productId);
    validateOwner(product, sellerId);

    ProductImage image =
        productImageRepository
            .findByIdAndProductId(imageId, productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
    String deletedImageUrl = image.getUrl();
    boolean thumbnailDeleted = image.isThumbnail();

    productImageRepository.delete(image);
    productImageRepository.flush();
    if (thumbnailDeleted) {
      productImageRepository
          .findFirstByProductIdOrderByCreatedAtAscIdAsc(productId)
          .ifPresent(ProductImage::markAsThumbnail);
      productImageRepository.flush();
    }

    registerAfterCommitDelete(deletedImageUrl);
    publishProductDisplayChanged(productId);
    return DeleteProductImageResponse.success();
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

  private void validateImagesPresent(List<MultipartFile> images) {
    if (images == null || images.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "업로드할 이미지는 하나 이상이어야 합니다.");
    }
  }

  private String validateImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
    if (image.getSize() > storageProperties.maxFileSize().toBytes()) {
      throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    String contentType = image.getContentType();
    if (contentType == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    String extension = SUPPORTED_IMAGE_EXTENSIONS.get(contentType.toLowerCase(Locale.ROOT));
    if (extension == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
    return extension;
  }

  private void registerRollbackCleanup(List<String> imageUrls) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              imageUrls.forEach(ProductImageService.this::deleteQuietly);
            }
          }
        });
  }

  private void registerAfterCommitDelete(String imageUrl) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      deleteQuietly(imageUrl);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            deleteQuietly(imageUrl);
          }
        });
  }

  private void deleteQuietly(String imageUrl) {
    try {
      productImageStorage.delete(imageUrl);
    } catch (RuntimeException exception) {
      log.warn("상품 이미지 파일 정리에 실패했습니다. imageUrl={}", imageUrl, exception);
    }
  }

  private void publishProductDisplayChanged(Long productId) {
    eventPublisher.publishEvent(new ProductDisplayChangedEvent(productId));
  }
}
