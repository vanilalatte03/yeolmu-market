package com.guingujig.yeolmumarket.domain.product.repository;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository
    extends JpaRepository<ProductImage, Long>, ProductImageRepositoryCustom {

  boolean existsByProductId(Long productId);

  List<ProductImage> findByProductIdOrderByCreatedAtAscIdAsc(Long productId);

  @EntityGraph(attributePaths = {"product", "product.seller"})
  @Query(
      """
      select image
      from ProductImage image
      where image.id = :imageId
        and image.product.id = :productId
        and image.product.deletedAt is null
        and image.product.status <> :deletedStatus
      """)
  Optional<ProductImage> findExistingImageWithProductAndSeller(
      @Param("imageId") Long imageId,
      @Param("productId") Long productId,
      @Param("deletedStatus") ProductStatus deletedStatus);

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
