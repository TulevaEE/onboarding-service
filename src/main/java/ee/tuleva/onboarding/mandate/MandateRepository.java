package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.user.User;
import org.springframework.data.repository.CrudRepository;

public interface MandateRepository extends CrudRepository<Mandate, Long> {

	Mandate findByIdAndUser(Long mandateId, User user);

}
