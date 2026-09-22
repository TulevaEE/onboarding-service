package ee.tuleva.onboarding.mandate;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface MandateRepository extends CrudRepository<Mandate, Long> {
  Mandate findByIdAndUserId(Long mandateId, Long userId);

  List<Mandate> findAllByUserIdAndCreatedDateAfter(Long userId, Instant createdDate);
}
