package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionBatchRepository extends JpaRepository<TransactionBatch, Long> {

  List<TransactionBatch> findByStatus(BatchStatus status);
}
