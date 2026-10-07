package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
class AdultAccountOpenedEmailSender {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_completed");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final SavingsFundFees savingsFundFees;
  private final AccountOpenedEmailClaims claims;

  void send(OpenedAccount account) {
    var email = account.email();
    if (email == null || !claims.claim(account.code())) {
      return;
    }
    var templateName = SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON.getTemplateName(ESTONIAN);
    var message = emailService.newMandrillMessage(email, templateName, mergeVars(account), TAGS);
    emailService
        .send(account, message, templateName)
        .ifPresent(response -> record(account, response));
  }

  private Map<String, Object> mergeVars(OpenedAccount account) {
    return Map.of(
        "fname", Names.formatted(account.firstName()),
        "lname", Names.formatted(account.lastName()),
        "savingsFundFee", savingsFundFees.ongoingChargesPercent(ESTONIAN),
        "awaitingFirstPayment", !account.paid());
  }

  private void record(OpenedAccount account, MandrillMessageStatus response) {
    try {
      emailPersistenceService.save(
          account,
          response.getId(),
          SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON,
          response.getStatus());
    } catch (RuntimeException e) {
      log.error(
          "Adult account opened email sent but not recorded: mandrillMessageId={}",
          response.getId(),
          e);
    }
  }
}
