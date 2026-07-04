package com.rastroos.domain.entity;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 40)
    private String id;

    @Column(name = "name_pt", nullable = false, length = 60)
    private String namePt;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "icon_name", nullable = false, length = 40)
    private String iconName;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public Category() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNamePt() { return namePt; }
    public void setNamePt(String namePt) { this.namePt = namePt; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public short getSortOrder() { return sortOrder; }
    public void setSortOrder(short sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Category{id=" + id + ", namePt=" + namePt + "}";
    }
}
