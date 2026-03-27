package et.trustlayer.tx.repository;

import et.trustlayer.common.entity.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserIdentityIdOrderByCreatedAtDesc(UUID userIdentityId);
    List<Transaction> findByMerchantIdOrderByCreatedAtDesc(String merchantId);
}
