package com.guingujig.yeolmumarket.domain.product.repository;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductImageRepositoryCustomImpl implements ProductImageRepositoryCustom {

  private final EntityManager entityManager;

  @Override
  public void deleteAndPromoteNextThumbnail(ProductImage image) {
    Long productId = image.getProduct().getId();
    boolean thumbnailDeleted = image.isThumbnail();

    entityManager.remove(entityManager.contains(image) ? image : entityManager.merge(image));
    if (thumbnailDeleted) {
      promoteFirstImageAsThumbnail(productId);
    }
  }

  private void promoteFirstImageAsThumbnail(Long productId) {
    entityManager.flush();
    entityManager
        .createQuery(
            """
            select image
            from ProductImage image
            where image.product.id = :productId
            order by image.createdAt asc, image.id asc
            """,
            ProductImage.class)
        .setParameter("productId", productId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .ifPresent(ProductImage::markAsThumbnail);
  }
}
