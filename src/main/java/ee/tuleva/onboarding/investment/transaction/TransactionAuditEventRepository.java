package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionAuditEventRepository
    extends JpaRepository<TransactionAuditEvent, Long> {

  List<TransactionAuditEvent> findByBatchIdOrderByCreatedAt(Long batchId);
}
