package ee.tuleva.onboarding.savings.fund.redemption.email;

import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.PreviousContactNotices;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class RedemptionEmailSender {

  private static final List<String> TAGS = List.of("savings_fund", "redemption");

  private final UserService userService;
  private final LocaleService localeService;
  private final RedemptionConfirmationMapper confirmationMapper;
  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final PreviousContactNotices previousContactNotices;

  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Transactional(propagation = NOT_SUPPORTED)
  public void onRedemptionRequested(RedemptionRequestedEvent event) {
    User requester;
    Locale locale;
    try {
      requester = userService.getByIdOrThrow(event.userId());
      locale = localeService.getCurrentLocale();
    } catch (RuntimeException e) {
      log.error(
          "Failed to load the redemption requester, no emails sent: redemptionRequestId={}, userId={}",
          event.redemptionRequestId(),
          event.userId(),
          e);
      return;
    }
    Optional<RedemptionConfirmation> confirmation = composeConfirmation(requester, event, locale);
    noticePreviousContacts(requester, event, locale);
    confirmation.ifPresent(composed -> confirm(requester, composed, event, locale));
  }

  private Optional<RedemptionConfirmation> composeConfirmation(
      User requester, RedemptionRequestedEvent event, Locale locale) {
    try {
      return Optional.of(confirmationMapper.map(requester, event, locale));
    } catch (RuntimeException e) {
      logConfirmationFailure(event, e);
      return Optional.empty();
    }
  }

  private void confirm(
      User requester,
      RedemptionConfirmation confirmation,
      RedemptionRequestedEvent event,
      Locale locale) {
    try {
      String templateName = confirmation.emailType().getTemplateName(locale);
      MandrillMessage message =
          emailService.newMandrillMessage(
              requester.getEmail(), templateName, confirmation.mergeVars(), TAGS);
      emailService
          .send(requester, message, templateName)
          .ifPresent(
              response ->
                  emailPersistenceService.save(
                      requester, response.getId(), confirmation.emailType(), response.getStatus()));
    } catch (RuntimeException e) {
      logConfirmationFailure(event, e);
    }
  }

  private static void logConfirmationFailure(RedemptionRequestedEvent event, RuntimeException e) {
    log.error(
        "Failed to confirm the redemption request: redemptionRequestId={}, userId={}",
        event.redemptionRequestId(),
        event.userId(),
        e);
  }

  private void noticePreviousContacts(
      User requester, RedemptionRequestedEvent event, Locale locale) {
    try {
      previousContactNotices.sendActivityNotice(requester, locale, event.requestedAt());
    } catch (RuntimeException e) {
      log.error(
          "Failed to notify previous contact: redemptionRequestId={}, userId={}",
          event.redemptionRequestId(),
          event.userId(),
          e);
    }
  }
}
