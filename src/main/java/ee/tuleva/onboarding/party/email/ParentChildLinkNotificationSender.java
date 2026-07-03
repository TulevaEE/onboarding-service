package ee.tuleva.onboarding.party.email;

import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.PARENT_CHILD_LINK_ADDED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.PARENT_CHILD_LINK_CONFIRMATION;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkCreatedEvent;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class ParentChildLinkNotificationSender {

  private static final Locale LOCALE = Locale.of("et");
  private static final List<String> TAGS = List.of("parent_child_link");

  private final EmailService emailService;
  private final EmailPersistenceService emailPersistenceService;
  private final UserService userService;
  private final ParentChildLinkRepository parentChildLinkRepository;
  private final Clock clock;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onParentChildLinkCreated(ParentChildLinkCreatedEvent event) {
    Optional<User> child = userService.findByPersonalCode(event.childPersonalCode());
    Optional<User> newParent = userService.findByPersonalCode(event.parentPersonalCode());

    if (child.isEmpty() || newParent.isEmpty()) {
      log.warn(
          "Cannot resolve users, skipping parent-child link notifications: parentCode={}, childCode={}, parentPresent={}, childPresent={}",
          event.parentPersonalCode(),
          event.childPersonalCode(),
          newParent.isPresent(),
          child.isPresent());
      return;
    }

    sendLinkConfirmation(newParent.get(), child.get());
    otherActiveParents(event)
        .forEach(existingParent -> sendLinkAdded(existingParent, newParent.get(), child.get()));
  }

  private List<User> otherActiveParents(ParentChildLinkCreatedEvent event) {
    return parentChildLinkRepository
        .findByChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
            event.childPersonalCode(), LocalDate.now(clock))
        .stream()
        .map(ParentChildLink::getParentPersonalCode)
        .filter(parentCode -> !parentCode.equals(event.parentPersonalCode()))
        .distinct()
        .map(userService::findByPersonalCode)
        .flatMap(Optional::stream)
        .toList();
  }

  private void sendLinkConfirmation(User newParent, User child) {
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(newParent));
    mergeVars.put("childName", child.name());
    send(newParent, PARENT_CHILD_LINK_CONFIRMATION, mergeVars);
  }

  private void sendLinkAdded(User recipient, User newParent, User child) {
    Map<String, Object> mergeVars = new HashMap<>(getNameMergeVars(recipient));
    mergeVars.put("newRepresentativeName", newParent.name());
    mergeVars.put("childName", child.name());
    send(recipient, PARENT_CHILD_LINK_ADDED, mergeVars);
  }

  private void send(User recipient, EmailType emailType, Map<String, Object> mergeVars) {
    String templateName = emailType.getTemplateName(LOCALE);
    try {
      MandrillMessage message =
          emailService.newMandrillMessage(
              recipient.getEmail(), templateName, mergeVars, TAGS, null);
      emailService
          .send(recipient, message, templateName)
          .ifPresent(
              response ->
                  emailPersistenceService.save(
                      recipient, response.getId(), emailType, response.getStatus()));
    } catch (Exception e) {
      log.error(
          "Failed to send parent-child link email: recipientId={}, templateName={}",
          recipient.getId(),
          templateName,
          e);
    }
  }
}
