package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduledEmailService {
  private final ScheduledEmailRepository scheduledEmailRepository;
  private final EmailService emailService;

  public void create(User user, String messageId, ScheduledEmailType type) {
    ScheduledEmail scheduledEmail = new ScheduledEmail(user.getId(), messageId, type);
    scheduledEmailRepository.save(scheduledEmail);
  }

  public void cancel(User user, ScheduledEmailType type) {
    List<ScheduledEmail> emails =
        scheduledEmailRepository.findAllByUserIdAndType(user.getId(), type);
    emails.forEach(email -> emailService.cancelScheduledEmail(email.getMandrillMessageId()));
    scheduledEmailRepository.deleteAll(emails);
  }
}
