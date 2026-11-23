package ee.tuleva.onboarding.notification.email;

import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.notification.email.PreviousContactNotice.Recipient;
import ee.tuleva.onboarding.user.ContactDetailsChanged;
import ee.tuleva.onboarding.user.RecentlyReplacedEmails;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreviousContactNotices {

  private static final List<String> TAGS = List.of("previous_contact");

  private final RecentlyReplacedEmails recentlyReplacedEmails;
  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final LocaleService localeService;

  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Transactional(propagation = NOT_SUPPORTED)
  public void onContactDetailsChanged(ContactDetailsChanged event) {
    User user = event.user();
    try {
      send(
          user,
          PreviousContactNotice.ofContactChange(
              user,
              event.changes(),
              recentlyReplacedEmails.of(user.getIdOrThrow(), event.changedAt())),
          localeService.getCurrentLocale());
    } catch (RuntimeException e) {
      log.error("Failed to notify previous contact: userId={}", user.getId(), e);
    }
  }

  public void sendActivityNotice(User user, Locale locale, Instant activityAt) {
    send(
        user,
        PreviousContactNotice.ofActivity(
            user, recentlyReplacedEmails.of(user.getIdOrThrow(), activityAt)),
        locale);
  }

  private void send(User user, PreviousContactNotice notice, Locale locale) {
    String templateName = notice.type().getTemplateName(locale);
    notice.recipients().forEach(recipient -> sendTo(recipient, user, notice.type(), templateName));
  }

  private void sendTo(Recipient recipient, User user, EmailType type, String templateName) {
    try {
      MandrillMessage message =
          emailService.newMandrillMessage(
              recipient.email(), templateName, recipient.mergeVars(), TAGS);
      emailService
          .send(user, message, templateName)
          .ifPresent(
              response ->
                  emailPersistenceService.save(user, response.getId(), type, response.getStatus()));
    } catch (RuntimeException e) {
      log.error("Failed to notify previous contact: userId={}, type={}", user.getId(), type, e);
    }
  }
}
