package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ScheduledEmailService {
  private final ScheduledEmailRepository scheduledEmailRepository;
  private final EmailService emailService;

  public void create(User user, String messageId, ScheduledEmailType type) {
    ScheduledEmail scheduledEmail = new ScheduledEmail(user.getId(), messageId, type);
    log.info("Scheduling an email: email={}", scheduledEmail);
    scheduledEmailRepository.save(scheduledEmail);
  }

  public void cancel(User user, ScheduledEmailType type) {
    List<ScheduledEmail> emails =
        scheduledEmailRepository.findAllByUserIdAndType(user.getId(), type);
    log.info("Cancelling scheduled emails: emails={}", emails);
    emails.forEach(email -> emailService.cancelScheduledEmail(email.getMandrillMessageId()));
    scheduledEmailRepository.deleteAll(emails);
  }
}
