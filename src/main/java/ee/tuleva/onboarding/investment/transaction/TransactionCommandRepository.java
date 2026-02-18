package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCommandRepository extends JpaRepository<TransactionCommand, Long> {

  List<TransactionCommand> findByStatus(CommandStatus status);
}
