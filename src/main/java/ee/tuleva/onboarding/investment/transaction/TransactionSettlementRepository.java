package ee.tuleva.onboarding.investment.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionSettlementRepository
    extends JpaRepository<TransactionSettlement, Long> {

  boolean existsByOrderId(Long orderId);

  Optional<TransactionSettlement> findByOrderId(Long orderId);
}
