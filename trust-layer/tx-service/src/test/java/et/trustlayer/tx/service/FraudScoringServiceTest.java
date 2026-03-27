package et.trustlayer.tx.service;

import static org.assertj.core.api.Assertions.assertThat;

import et.trustlayer.common.entity.Transaction;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FraudScoringServiceTest {

    private final FraudScoringService service = new FraudScoringService();

    @Test
    void blocksWhenAmountExceedsSingleTxLimit() {
        FraudScoringService.FraudScoreResult result = service.score(
            200_000L,
            100_000L,
            List.of()
        );
        assertThat(result.score()).isGreaterThanOrEqualTo(60);
        assertThat(result.decision()).isIn("REVIEW", "BLOCK");
        assertThat(result.flags()).contains("LIMIT_EXCEEDED");
    }

    @Test
    void addsVelocityFlagWhenManyRecentTransactions() {
        List<Transaction> recent = List.of(
            transactionNow(),
            transactionNow(),
            transactionNow(),
            transactionNow(),
            transactionNow()
        );
        FraudScoringService.FraudScoreResult result = service.score(50_000L, 100_000L, recent);
        assertThat(result.flags()).contains("VELOCITY_HIGH");
    }

    private Transaction transactionNow() {
        return Transaction.builder()
            .merchantId("merchant")
            .amountMinor(1_000L)
            .status("APPROVED")
            .currency("ETB")
            .createdAt(LocalDateTime.now())
            .build();
    }
}
