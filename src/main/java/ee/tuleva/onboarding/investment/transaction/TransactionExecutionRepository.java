package ee.tuleva.onboarding.investment.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionExecutionRepository extends JpaRepository<TransactionExecution, Long> {

  Optional<TransactionExecution> findByOrderId(Long orderId);

  Optional<TransactionExecution> findByBrokerTransactionId(String brokerTransactionId);
}
