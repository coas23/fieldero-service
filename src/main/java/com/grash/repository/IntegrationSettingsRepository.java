package com.grash.repository;

import com.grash.model.IntegrationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationSettingsRepository extends JpaRepository<IntegrationSettings, Long> {
    Optional<IntegrationSettings> findByCompanySettings_Id(Long id);
}
