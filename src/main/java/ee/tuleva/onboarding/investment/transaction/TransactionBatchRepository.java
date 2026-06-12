package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionBatchRepository extends JpaRepository<TransactionBatch, Long> {

  List<TransactionBatch> findByStatus(BatchStatus status);

  Optional<TransactionBatch> findFirstByFundOrderByCreatedAtDesc(TulevaFund fund);
}
