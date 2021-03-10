package ee.tuleva.onboarding.mandate;

import org.springframework.data.repository.CrudRepository;

public interface MandateRepository extends CrudRepository<Mandate, Long> {

  Mandate findByIdAndUserId(Long mandateId, Long userId);
}
