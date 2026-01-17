package ee.tuleva.onboarding.banking.message;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface BankingMessageRepository extends CrudRepository<BankingMessage, UUID> {

  List<BankingMessage> findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc();
}
