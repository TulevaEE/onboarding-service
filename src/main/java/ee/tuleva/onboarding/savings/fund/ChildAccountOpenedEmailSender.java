package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static java.util.Locale.ENGLISH;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
class ChildAccountOpenedEmailSender {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_completed");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final UserService userService;
  private final ParentChildLinkService parentChildLinkService;
  private final SavingsFundFees savingsFundFees;
  private final AccountOpenedEmailClaims claims;
  private final OpenedAccountRepository openedAccounts;

  void send(OpenedAccount child) {
    parentChildLinkService
        .findFirstActiveRepresentative(child.code())
        .ifPresent(parentCode -> emailParent(parentCode, child));
  }

  private void emailParent(String parentCode, OpenedAccount child) {
    UUID accountId =
        parentChildLinkService
            .findRepresentation(parentCode, child.code(), Set.of(ACTIVE))
            .orElseThrow();
    boolean hasCoParent =
        parentChildLinkService.hasPendingRepresentativeOtherThan(child.code(), parentCode);
    var locale = openedAccounts.prefersEnglish(parentCode) ? ENGLISH : ESTONIAN;
    userService
        .findByPersonalCode(parentCode)
        .filter(parent -> parent.getEmail() != null && !parent.getEmail().isBlank())
        .ifPresentOrElse(
            parent ->
                send(
                    parent,
                    child,
                    accountId,
                    locale,
                    mergeVars(parent, accountId, child, hasCoParent, locale)),
            () ->
                log.warn(
                    "Parent has no email, skipping the child account opened email: accountId={}",
                    accountId));
  }

  private Map<String, Object> mergeVars(
      User parent, UUID accountId, OpenedAccount child, boolean hasCoParent, Locale locale) {
    var mergeVars = new HashMap<String, Object>(getNameMergeVars(parent));
    mergeVars.put("recipientName", Names.formatted(child.firstName() + " " + child.lastName()));
    mergeVars.put("recipientAccountId", accountId.toString());
    mergeVars.put("savingsFundFee", savingsFundFees.ongoingChargesPercent(locale));
    mergeVars.put("hasCoParent", hasCoParent);
    mergeVars.put("awaitingFirstPayment", !child.paid());
    return mergeVars;
  }

  private void send(
      User parent,
      OpenedAccount child,
      UUID accountId,
      Locale locale,
      Map<String, Object> mergeVars) {
    if (!claims.claim(child.code())) {
      log.info("Child account opened email already claimed, skipping: accountId={}", accountId);
      return;
    }
    var templateName = SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD.getTemplateName(locale);
    var message = emailService.newMandrillMessage(parent.getEmail(), templateName, mergeVars, TAGS);
    emailService
        .send(parent, message, templateName)
        .ifPresentOrElse(
            response -> record(child, accountId, response), () -> claims.release(child.code()));
  }

  private void record(OpenedAccount child, UUID accountId, MandrillMessageStatus response) {
    try {
      emailPersistenceService.save(
          child, response.getId(), SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD, response.getStatus());
    } catch (RuntimeException e) {
      log.error(
          "Child account opened email sent but not recorded: accountId={}, mandrillMessageId={}",
          accountId,
          response.getId(),
          e);
    }
  }
}
