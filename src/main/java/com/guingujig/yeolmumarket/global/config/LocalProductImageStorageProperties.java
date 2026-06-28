package com.guingujig.yeolmumarket.global.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "yeolmu.storage.local")
public record LocalProductImageStorageProperties(
    Path rootPath, String publicBaseUrl, DataSize maxFileSize) {

  private static final Path DEFAULT_ROOT_PATH = Path.of("build/uploads");
  private static final String DEFAULT_PUBLIC_BASE_URL = "/uploads";
  private static final DataSize DEFAULT_MAX_FILE_SIZE = DataSize.ofMegabytes(5);

  public LocalProductImageStorageProperties {
    if (rootPath == null) {
      rootPath = DEFAULT_ROOT_PATH;
    }
    if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
      publicBaseUrl = DEFAULT_PUBLIC_BASE_URL;
    }
    if (maxFileSize == null) {
      maxFileSize = DEFAULT_MAX_FILE_SIZE;
    }
  }
}
