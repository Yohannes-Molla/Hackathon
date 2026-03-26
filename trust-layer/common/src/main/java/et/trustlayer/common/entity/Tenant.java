package et.trustlayer.common.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String domain;

    @Column(name = "mtls_cert_thumbprint")
    private String mtlsCertThumbprint;

    @Builder.Default
    @Column(name = "primary_color")
    private String primaryColor = "#6366f1";

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "custom_css", columnDefinition = "TEXT")
    private String customCss;

    @Builder.Default
    @Column(name = "trust_framework", nullable = false)
    private String trustFramework = "et_nbe_kyc";

    @Builder.Default
    @Column(name = "is_hub", nullable = false)
    private boolean hub = false;

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
