package com.guingujig.yeolmumarket.domain.product.service;

import org.springframework.web.multipart.MultipartFile;

public interface ProductImageStorage {

  StoredProductImage store(Long productId, MultipartFile file, String extension);

  void delete(String imageUrl);
}
