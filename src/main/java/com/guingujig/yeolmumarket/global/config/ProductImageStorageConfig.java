package com.guingujig.yeolmumarket.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LocalProductImageStorageProperties.class)
public class ProductImageStorageConfig implements WebMvcConfigurer {

  private final LocalProductImageStorageProperties properties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (properties.publicBaseUrl().startsWith("/")) {
      registry
          .addResourceHandler(normalizeBaseUrl(properties.publicBaseUrl()) + "/**")
          .addResourceLocations(properties.rootPath().toUri().toString());
    }
  }

  private String normalizeBaseUrl(String publicBaseUrl) {
    if (publicBaseUrl.endsWith("/")) {
      return publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
    }
    return publicBaseUrl;
  }
}
