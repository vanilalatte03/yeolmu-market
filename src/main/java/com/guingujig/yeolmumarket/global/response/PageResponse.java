package com.guingujig.yeolmumarket.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 API가 공통으로 사용하는 페이지 응답 DTO다.
 *
 * <p>Spring Data {@link Page}를 API 문서의 페이지 응답 필드로 고정해 Controller마다 페이지 메타데이터를 다르게 만들지 않도록 한다.
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
