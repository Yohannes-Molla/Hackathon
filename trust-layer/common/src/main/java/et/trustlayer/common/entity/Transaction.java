package et.trustlayer.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "transactions")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_identity_id", nullable = false)
    private UserIdentity userIdentity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_card_id")
    private VirtualCard virtualCard;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "ETB";

    @Column(nullable = false)
    private String status;

    @Column(name = "nonce")
    private String nonce;

    @Builder.Default
    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified = false;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Column(name = "fraud_flags")
    private String fraudFlags;

    @Builder.Default
    @Version
    private Long version = 0L;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
