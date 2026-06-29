package com.guingujig.yeolmumarket.domain.product.repository;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

  boolean existsByProductId(Long productId);

  List<ProductImage> findByProductIdOrderByCreatedAtAscIdAsc(Long productId);

  Optional<ProductImage> findByIdAndProductId(Long imageId, Long productId);

  Optional<ProductImage> findFirstByProductIdOrderByCreatedAtAscIdAsc(Long productId);

  @Query(
      """
      select image.product.id as productId, image.url as url
      from ProductImage image
      where image.product.id in :productIds
        and image.thumbnail = true
      """)
  List<ProductThumbnailUrl> findThumbnailUrlsByProductIds(
      @Param("productIds") Collection<Long> productIds);

  interface ProductThumbnailUrl {
    Long getProductId();

    String getUrl();
  }
}
