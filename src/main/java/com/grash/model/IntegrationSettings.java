package com.grash.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.Transient;

@Entity
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "companySettings")
public class IntegrationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @JsonIgnore
    private String lexwareSecretEncrypted;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean lexwareSecretConfigured;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_settings_id", nullable = false)
    @JsonIgnore
    private CompanySettings companySettings;

    public IntegrationSettings(CompanySettings companySettings) {
        this.companySettings = companySettings;
    }

    public void setLexwareSecretEncrypted(String lexwareSecretEncrypted) {
        this.lexwareSecretEncrypted = lexwareSecretEncrypted;
        refreshComputedFields();
    }

    @PostLoad
    @PostPersist
    @PostUpdate
    private void refreshComputedFields() {
        this.lexwareSecretConfigured = StringUtils.hasText(this.lexwareSecretEncrypted);
    }
}
