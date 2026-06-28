package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.global.config.LocalProductImageStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class LocalProductImageStorage implements ProductImageStorage {

  private final LocalProductImageStorageProperties properties;

  @Override
  public StoredProductImage store(Long productId, MultipartFile file, String extension) {
    Path directory = rootPath().resolve("products").resolve(productId.toString()).normalize();
    String filename = UUID.randomUUID() + extension;
    Path target = directory.resolve(filename).normalize();

    try {
      Files.createDirectories(directory);
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, target);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("상품 이미지 파일 저장에 실패했습니다.", exception);
    }

    return new StoredProductImage(
        normalizeBaseUrl(properties.publicBaseUrl()) + "/products/" + productId + "/" + filename);
  }

  @Override
  public void delete(String imageUrl) {
    String baseUrl = normalizeBaseUrl(properties.publicBaseUrl());
    if (!imageUrl.startsWith(baseUrl + "/")) {
      return;
    }

    Path rootPath = rootPath();
    Path target = rootPath.resolve(imageUrl.substring(baseUrl.length() + 1)).normalize();
    if (!target.startsWith(rootPath)) {
      return;
    }

    try {
      Files.deleteIfExists(target);
    } catch (IOException exception) {
      throw new IllegalStateException("상품 이미지 파일 삭제에 실패했습니다.", exception);
    }
  }

  private Path rootPath() {
    return properties.rootPath().toAbsolutePath().normalize();
  }

  private String normalizeBaseUrl(String publicBaseUrl) {
    if (publicBaseUrl.endsWith("/")) {
      return publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
    }
    return publicBaseUrl;
  }
}
