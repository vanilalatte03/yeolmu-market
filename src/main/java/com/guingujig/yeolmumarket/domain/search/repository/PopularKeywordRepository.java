package com.guingujig.yeolmumarket.domain.search.repository;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import java.util.List;

public interface PopularKeywordRepository {

  void incrementSearchCount(String keyword);

  List<PopularKeyword> findTopKeywords(int limit);
}
