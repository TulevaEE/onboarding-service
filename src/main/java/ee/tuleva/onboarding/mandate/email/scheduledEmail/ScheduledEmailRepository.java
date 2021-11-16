package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import org.springframework.data.repository.CrudRepository;

public interface ScheduledEmailRepository extends CrudRepository<ScheduledEmail, Long> {

  ScheduledEmail findByMandrillMessageId(String mandrillMessageId);
}
