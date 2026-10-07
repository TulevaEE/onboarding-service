package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.LocalDate;
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
  private final ChildAccountOpenedEmailClaims claims;
  private final Clock clock;

  void send(OpenedChildAccount account) {
    if (!isMinor(account)) {
      return;
    }
    parentChildLinkService
        .findFirstActiveRepresentative(account.childCode())
        .ifPresent(parentCode -> emailParent(parentCode, account));
  }

  private boolean isMinor(OpenedChildAccount account) {
    try {
      return PersonalCode.isMinor(account.childCode(), LocalDate.now(clock));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private void emailParent(String parentCode, OpenedChildAccount account) {
    UUID accountId =
        parentChildLinkService
            .findRepresentation(parentCode, account.childCode(), Set.of(ACTIVE))
            .orElseThrow();
    boolean hasCoParent =
        parentChildLinkService.hasPendingRepresentativeOtherThan(account.childCode(), parentCode);
    userService
        .findByPersonalCode(parentCode)
        .filter(parent -> parent.getEmail() != null && !parent.getEmail().isBlank())
        .ifPresentOrElse(
            parent ->
                send(
                    parent, account, accountId, mergeVars(parent, accountId, account, hasCoParent)),
            () ->
                log.warn(
                    "Parent has no email, skipping the child account opened email: accountId={}",
                    accountId));
  }

  private Map<String, Object> mergeVars(
      User parent, UUID accountId, OpenedChildAccount account, boolean hasCoParent) {
    var mergeVars = new HashMap<String, Object>(getNameMergeVars(parent));
    mergeVars.put("recipientName", Names.formatted(account.firstName() + " " + account.lastName()));
    mergeVars.put("recipientAccountId", accountId.toString());
    mergeVars.put("savingsFundFee", savingsFundFees.ongoingChargesPercent(ESTONIAN));
    mergeVars.put("hasCoParent", hasCoParent);
    mergeVars.put("awaitingFirstPayment", !account.paid());
    return mergeVars;
  }

  private void send(
      User parent, OpenedChildAccount account, UUID accountId, Map<String, Object> mergeVars) {
    if (!claims.claim(account.childCode())) {
      log.info("Child account opened email already claimed, skipping: accountId={}", accountId);
      return;
    }
    var templateName = SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD.getTemplateName(ESTONIAN);
    var message = emailService.newMandrillMessage(parent.getEmail(), templateName, mergeVars, TAGS);
    emailService
        .send(parent, message, templateName)
        .ifPresent(response -> record(account, accountId, response));
  }

  private void record(OpenedChildAccount account, UUID accountId, MandrillMessageStatus response) {
    try {
      emailPersistenceService.save(
          account, response.getId(), SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD, response.getStatus());
    } catch (RuntimeException e) {
      log.error(
          "Child account opened email sent but not recorded, it may be sent again: accountId={}, mandrillMessageId={}",
          accountId,
          response.getId(),
          e);
    }
  }
}
