package et.trustlayer.authserver.repository;

import et.trustlayer.common.entity.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserIdentityIdOrderByCreatedAtDesc(UUID userIdentityId);
}
