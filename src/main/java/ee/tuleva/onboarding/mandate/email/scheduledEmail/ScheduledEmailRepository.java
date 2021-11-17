package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface ScheduledEmailRepository extends CrudRepository<ScheduledEmail, Long> {

  List<ScheduledEmail> findAllByUserIdAndType(long userId, ScheduledEmailType type);
}
