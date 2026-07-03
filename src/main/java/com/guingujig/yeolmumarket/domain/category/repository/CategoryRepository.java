package com.guingujig.yeolmumarket.domain.category.repository;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, Long id);
}
