package ee.tuleva.onboarding.swedbank.fetcher;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SwedbankMessageRepository extends CrudRepository<SwedbankMessage, UUID> {

  List<SwedbankMessage> findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc();
}
