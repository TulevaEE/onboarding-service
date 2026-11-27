package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionBatchRepository
    extends JpaRepository<TransactionBatch, Long>, TransactionBatchLocking {

  Optional<TransactionBatch> findFirstByFundOrderByCreatedAtDesc(TulevaFund fund);
}
