package et.trustlayer.tx.service;

import et.trustlayer.common.entity.Transaction;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.springframework.stereotype.Service;

@Service
public class FraudScoringService {

    public FraudScoreResult score(
        long amountMinor,
        long singleTxLimitMinor,
        List<Transaction> recentTransactions
    ) {
        int score = 0;
        List<String> flags = new ArrayList<>();

        if (amountMinor > singleTxLimitMinor) {
            score += 60;
            flags.add("LIMIT_EXCEEDED");
        } else if (amountMinor > (singleTxLimitMinor * 0.8)) {
            score += 25;
            flags.add("HIGH_AMOUNT");
        }

        long lastMinuteCount = recentTransactions.stream()
            .filter(tx -> tx.getCreatedAt() != null && tx.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1)))
            .count();
        if (lastMinuteCount >= 5) {
            score += 30;
            flags.add("VELOCITY_HIGH");
        }

        int utcHour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        if (utcHour < 4 || utcHour > 23) {
            score += 10;
            flags.add("OFF_HOURS");
        }

        if (score > 100) {
            score = 100;
        }

        String decision = "APPROVE";
        if (score >= 70) {
            decision = "BLOCK";
        } else if (score >= 40) {
            decision = "REVIEW";
        }

        return FraudScoreResult.builder()
            .score(score)
            .decision(decision)
            .flags(flags)
            .build();
    }

    @Builder
    public record FraudScoreResult(
        int score,
        String decision,
        List<String> flags
    ) {
    }
}
