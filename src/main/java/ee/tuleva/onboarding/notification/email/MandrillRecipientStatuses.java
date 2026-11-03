package ee.tuleva.onboarding.notification.email;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.failed;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.sent;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.unknown;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
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
import java.util.stream.Stream;
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
    return new MandrillRecipientStatuses(
        Set.copyOf(mainRecipients), Set.copyOf(copyOnlyRecipients), List.of(statuses));
  }

  void requireAcceptedByEveryMainRecipient() {
    if (statuses.isEmpty()) {
      throw new IllegalStateException(noStatusProblem());
    }
    var result = result();
    if (result.outcome() != SENT) {
      throw new EmailDeliveryException(requireNonNull(result.problem()));
    }
  }

  EmailSendResult result() {
    if (statuses.isEmpty()) {
      return unknown(statuses, noStatusProblem());
    }
    statuses.forEach(MandrillRecipientStatuses::logStatus);
    statuses.stream()
        .filter(this::isOnlyCopy)
        .filter(not(MandrillRecipientStatuses::isAccepted))
        .forEach(MandrillRecipientStatuses::warnCopyNotAccepted);
    if (!answeredRecipients().containsAll(mainRecipients)) {
      return unknown(statuses, firstRefusal().orElseGet(this::missingMainRecipientProblem));
    }
    if (mainStatuses().allMatch(MandrillRecipientStatuses::isAccepted)) {
      return sent(statuses);
    }
    var problem = firstRefusal().orElseThrow();
    return mainStatuses().allMatch(MandrillRecipientStatuses::hasFailed)
        ? failed(statuses, problem)
        : unknown(statuses, problem);
  }

  private Stream<MandrillMessageStatus> mainStatuses() {
    return statuses.stream().filter(not(this::isOnlyCopy));
  }

  private Optional<String> firstRefusal() {
    return mainStatuses()
        .filter(not(MandrillRecipientStatuses::isAccepted))
        .findFirst()
        .map(
            status ->
                "Mandrill did not accept email: status=%s, rejectReason=%s, id=%s"
                    .formatted(status.getStatus(), status.getRejectReason(), status.getId()));
  }

  private String missingMainRecipientProblem() {
    return "Mandrill gave no status for a main recipient: mainRecipients=%d, statuses=%d, ids=%s"
        .formatted(
            mainRecipients.size(),
            statuses.size(),
            statuses.stream().map(MandrillMessageStatus::getId).toList());
  }

  private String noStatusProblem() {
    return "Mandrill answered with no recipient status: mainRecipients=%d, copyRecipients=%d"
        .formatted(mainRecipients.size(), copyOnlyRecipients.size());
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

  static boolean isAccepted(MandrillMessageStatus status) {
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
