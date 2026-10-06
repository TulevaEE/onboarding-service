package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.auth.principal.Person;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class ChildAccountOpenedEmailSender {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final List<String> TAGS = List.of("savings_fund", "onboarding_completed");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final UserService userService;
  private final ParentChildLinkService parentChildLinkService;
  private final SavingsFundFees savingsFundFees;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOnboardingCompleted(SavingsFundOnboardingCompletedEvent event) {
    Person child = event.person();
    try {
      if (!isMinor(child)) {
        return;
      }
      parentChildLinkService
          .findFirstActiveRepresentative(child.getPersonalCode())
          .ifPresent(parentCode -> emailParent(parentCode, child));
    } catch (RuntimeException e) {
      log.error(
          "Failed to send the child account opened email: childCode={}",
          child.getPersonalCode(),
          e);
    }
  }

  private boolean isMinor(Person person) {
    try {
      return PersonalCode.isMinor(person.getPersonalCode(), LocalDate.now(clock));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private void emailParent(String parentCode, Person child) {
    UUID accountId =
        parentChildLinkService
            .findRepresentation(parentCode, child.getPersonalCode(), Set.of(ACTIVE))
            .orElseThrow();
    boolean hasCoParent = parentChildLinkService.hasPendingRepresentative(child.getPersonalCode());
    userService
        .findByPersonalCode(parentCode)
        .filter(parent -> parent.getEmail() != null && !parent.getEmail().isBlank())
        .ifPresentOrElse(
            parent -> send(parent, child, mergeVars(parent, accountId, child, hasCoParent)),
            () ->
                log.warn(
                    "Parent has no email, skipping the child account opened email: parentCode={}, childCode={}",
                    parentCode,
                    child.getPersonalCode()));
  }

  private Map<String, Object> mergeVars(
      User parent, UUID accountId, Person child, boolean hasCoParent) {
    var mergeVars = new HashMap<String, Object>(getNameMergeVars(parent));
    mergeVars.put(
        "recipientName", Names.formatted(child.getFirstName() + " " + child.getLastName()));
    mergeVars.put("recipientAccountId", accountId.toString());
    mergeVars.put("savingsFundFee", savingsFundFees.ongoingChargesPercent(ESTONIAN));
    mergeVars.put("hasCoParent", hasCoParent);
    return mergeVars;
  }

  private void send(User parent, Person child, Map<String, Object> mergeVars) {
    var templateName = SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD.getTemplateName(ESTONIAN);
    var message = emailService.newMandrillMessage(parent.getEmail(), templateName, mergeVars, TAGS);
    emailService
        .send(parent, message, templateName)
        .ifPresent(response -> recordAgainstTheChild(child, response));
  }

  private void recordAgainstTheChild(Person child, MandrillMessageStatus response) {
    try {
      transactionOfItsOwn()
          .executeWithoutResult(
              status ->
                  emailPersistenceService.save(
                      child,
                      response.getId(),
                      SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD,
                      response.getStatus()));
    } catch (RuntimeException e) {
      log.error(
          "Child account opened email sent but not recorded: childCode={}, mandrillMessageId={}",
          child.getPersonalCode(),
          response.getId(),
          e);
    }
  }

  private TransactionTemplate transactionOfItsOwn() {
    var transactionOfItsOwn = new TransactionTemplate(transactionManager);
    transactionOfItsOwn.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    return transactionOfItsOwn;
  }
}
