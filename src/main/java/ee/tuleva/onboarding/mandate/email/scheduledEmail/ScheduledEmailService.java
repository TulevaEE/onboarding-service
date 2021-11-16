package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import ee.tuleva.onboarding.user.User;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduledEmailService {
  private final ScheduledEmailRepository scheduledEmailRepository;

  public void create(User user, String messageId, ScheduledEmailType type) {
    ScheduledEmail scheduledEmail = new ScheduledEmail(user.getId(), messageId, type);
    scheduledEmailRepository.save(scheduledEmail);
  }
}
