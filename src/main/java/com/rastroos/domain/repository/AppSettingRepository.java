package com.rastroos.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
