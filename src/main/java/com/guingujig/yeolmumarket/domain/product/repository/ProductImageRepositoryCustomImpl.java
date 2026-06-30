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
        .createNativeQuery(
            """
            update product_image
            set is_thumbnail = true
            where id = (
              select id
              from (
                select candidate.id
                from product_image candidate
                where candidate.product_id = :productId
                order by candidate.created_at asc, candidate.id asc
                limit 1
              ) next_image
            )
            """)
        .setParameter("productId", productId)
        .executeUpdate();
  }
}
