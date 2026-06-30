package com.guingujig.yeolmumarket.domain.search.service;

public interface SearchIndexVersionProvider {

  String currentVersionKey();

  void increaseVersion();
}
