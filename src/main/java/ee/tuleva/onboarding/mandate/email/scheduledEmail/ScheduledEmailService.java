package ee.tuleva.onboarding.mandate.email.scheduledEmail;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
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
    create(user, messageId, type, null);
  }

  public void create(User user, String messageId, ScheduledEmailType type, Mandate mandate) {
    ScheduledEmail scheduledEmail =
        ScheduledEmail.builder()
            .userId(user.getId())
            .mandrillMessageId(messageId)
            .type(type)
            .mandate(mandate)
            .build();
    log.info("Scheduling an email: email={}", scheduledEmail);
    scheduledEmailRepository.save(scheduledEmail);
  }

  public List<ScheduledEmail> cancel(User user, ScheduledEmailType type) {
    List<ScheduledEmail> emails =
        scheduledEmailRepository.findAllByUserIdAndTypeOrderByCreatedDateDesc(user.getId(), type);
    log.info("Cancelling scheduled emails: emails={}", emails);
    List<ScheduledEmail> cancelled = new ArrayList<>();
    emails.forEach(
        email ->
            emailService
                .cancelScheduledEmail(email.getMandrillMessageId())
                .ifPresent(info -> cancelled.add(email)));
    scheduledEmailRepository.deleteAll(emails);
    return cancelled;
  }
}
