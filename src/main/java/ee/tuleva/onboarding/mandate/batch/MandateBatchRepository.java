package ee.tuleva.onboarding.mandate.batch;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface MandateBatchRepository extends CrudRepository<MandateBatch, Long> {

  List<MandateBatch> findAllByStatusAndCreatedDateBetweenOrderByCreatedDate(
      MandateBatchStatus status, Instant from, Instant to);
}
