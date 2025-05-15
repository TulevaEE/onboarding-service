package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeTransactionSnapshotRepository
    extends JpaRepository<ExchangeTransactionSnapshot, Long> {}
