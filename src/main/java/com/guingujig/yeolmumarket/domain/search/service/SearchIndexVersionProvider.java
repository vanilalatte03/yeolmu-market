package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.Collection;

public interface SearchIndexVersionProvider {

  String currentVersionKey(ProductStatus status);

  void increaseVersions(Collection<ProductStatus> statuses);
}
