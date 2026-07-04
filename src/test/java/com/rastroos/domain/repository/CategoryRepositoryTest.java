package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.Category;

/**
 * Smoke do schema: o seed do changelog 002 grava 11 categorias.
 */
class CategoryRepositoryTest extends RepositoryTestBase {

    @Autowired
    private CategoryRepository categories;

    @Test
    void seedDeveTer11CategoriasOrdenadasPorSortOrder() {
        List<Category> all = categories.findAllByOrderBySortOrderAsc();

        assertThat(all).hasSize(11);
        assertThat(all.get(0).getId()).isEqualTo("moradia");
        assertThat(all.get(all.size() - 1).getId()).isEqualTo("outros");
        assertThat(all).extracting(Category::getColorHex)
                .allMatch(c -> c.startsWith("#") && c.length() == 7);
    }
}
