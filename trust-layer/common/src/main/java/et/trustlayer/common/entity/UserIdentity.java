package et.trustlayer.common.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_identity")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "external_sub", nullable = false)
    private String externalSub;

    @Column(name = "keycloak_sub")
    private String keycloakSub;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String nationality;

    @Builder.Default
    @Column(name = "assurance_level", nullable = false)
    private String assuranceLevel = "ial2";

    @Column(name = "ekyc_verification_id")
    private String ekycVerificationId;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "trust_framework", nullable = false)
    private String trustFramework;

    @Builder.Default
    @Column(name = "onboarding_state", nullable = false)
    private String onboardingState = "PENDING";

    @Builder.Default
    @Version
    private Long version = 0L;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
