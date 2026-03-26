package et.trustlayer.common.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "virtual_card")
@FilterDef(name = "tenantFilter", parameters = {@ParamDef(name = "tenantId", type = UUID.class)})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VirtualCard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_identity_id", nullable = false)
    private UserIdentity userIdentity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bound_credential_id", nullable = false)
    private BiometricCredential boundCredential;

    @Column(name = "pan_token", nullable = false)
    private String panToken;

    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    @Column(name = "card_network", nullable = false)
    private String cardNetwork;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "ETB";

    @Builder.Default
    @Column(name = "daily_limit_minor", nullable = false)
    private Long dailyLimitMinor = 500000L;

    @Builder.Default
    @Column(name = "single_tx_limit_minor", nullable = false)
    private Long singleTxLimitMinor = 100000L;

    @Builder.Default
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Builder.Default
    @Version
    private Long version = 0L;

    @Builder.Default
    @Column(name = "provisioned_at", nullable = false, updatable = false)
    private LocalDateTime provisionedAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        provisionedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
