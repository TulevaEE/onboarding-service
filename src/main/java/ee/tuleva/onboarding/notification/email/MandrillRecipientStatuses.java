package ee.tuleva.onboarding.notification.email;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static java.util.Locale.ROOT;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
record MandrillRecipientStatuses(
    Set<String> mainRecipients,
    Set<String> copyOnlyRecipients,
    List<MandrillMessageStatus> statuses) {

  private static final Set<String> ACCEPTED_STATUSES = Set.of("sent", "queued", "scheduled");
  private static final Set<String> FAILED_STATUSES = Set.of("rejected", "invalid");

  static MandrillRecipientStatuses of(MandrillMessage message, MandrillMessageStatus[] statuses) {
    Set<String> mainRecipients = addressesOf(message, false);
    Set<String> copyOnlyRecipients = addressesOf(message, true);
    copyOnlyRecipients.removeAll(mainRecipients);
    if (statuses.length == 0) {
      throw new IllegalStateException(
          "Mandrill answered with no recipient status: mainRecipients=%d, copyRecipients=%d"
              .formatted(mainRecipients.size(), copyOnlyRecipients.size()));
    }
    return new MandrillRecipientStatuses(
        Set.copyOf(mainRecipients), Set.copyOf(copyOnlyRecipients), List.of(statuses));
  }

  void requireAcceptedByEveryMainRecipient() {
    statuses.forEach(MandrillRecipientStatuses::logStatus);
    List<MandrillMessageStatus> notAccepted =
        statuses.stream().filter(not(MandrillRecipientStatuses::isAccepted)).toList();
    notAccepted.stream()
        .filter(this::isOnlyCopy)
        .forEach(MandrillRecipientStatuses::warnCopyNotAccepted);
    notAccepted.stream()
        .filter(not(this::isOnlyCopy))
        .findFirst()
        .ifPresent(
            status -> {
              throw new EmailDeliveryException(
                  "Mandrill did not accept email: status=%s, rejectReason=%s, id=%s"
                      .formatted(status.getStatus(), status.getRejectReason(), status.getId()));
            });
    if (!answeredRecipients().containsAll(mainRecipients)) {
      throw new EmailDeliveryException(
          "Mandrill gave no status for a main recipient: mainRecipients=%d, statuses=%d, ids=%s"
              .formatted(
                  mainRecipients.size(),
                  statuses.size(),
                  statuses.stream().map(MandrillMessageStatus::getId).toList()));
    }
  }

  static void logStatus(MandrillMessageStatus status) {
    log.info(
        "Mandrill API response: status={}, id={}, rejectReason={}",
        status.getStatus(),
        status.getId(),
        status.getRejectReason());
  }

  static boolean hasFailed(MandrillMessageStatus status) {
    return status.getStatus() != null && FAILED_STATUSES.contains(status.getStatus());
  }

  private boolean isOnlyCopy(MandrillMessageStatus status) {
    @Nullable String address = normalized(status);
    return address != null && !mainRecipients.isEmpty() && copyOnlyRecipients.contains(address);
  }

  private Set<String> answeredRecipients() {
    return statuses.stream()
        .map(MandrillRecipientStatuses::normalized)
        .filter(Objects::nonNull)
        .collect(toSet());
  }

  private static Set<String> addressesOf(MandrillMessage message, boolean copies) {
    return Optional.ofNullable(message.getTo()).orElse(List.of()).stream()
        .filter(recipient -> isCopy(recipient) == copies)
        .map(Recipient::getEmail)
        .filter(Objects::nonNull)
        .map(MandrillRecipientStatuses::normalized)
        .collect(toCollection(HashSet::new));
  }

  private static boolean isCopy(Recipient recipient) {
    return recipient.getType() == CC || recipient.getType() == BCC;
  }

  private static boolean isAccepted(MandrillMessageStatus status) {
    return status.getStatus() != null && ACCEPTED_STATUSES.contains(status.getStatus());
  }

  private static @Nullable String normalized(MandrillMessageStatus status) {
    @Nullable String email = status.getEmail();
    return email == null ? null : normalized(email);
  }

  private static String normalized(String email) {
    return email.strip().toLowerCase(ROOT);
  }

  private static void warnCopyNotAccepted(MandrillMessageStatus status) {
    log.warn(
        "Mandrill did not accept a copy recipient, the email still counts as sent: status={}, rejectReason={}, id={}",
        status.getStatus(),
        status.getRejectReason(),
        status.getId());
  }
}
