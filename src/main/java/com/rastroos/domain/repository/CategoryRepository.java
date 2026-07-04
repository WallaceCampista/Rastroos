package com.rastroos.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findAllByOrderBySortOrderAsc();
}
