package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.UNKNOWN;
import static java.util.function.Predicate.not;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record EmailSendResult(
    Outcome outcome, List<MandrillMessageStatus> statuses, @Nullable String problem) {

  public enum Outcome {
    SENT,
    FAILED,
    UNKNOWN
  }

  public EmailSendResult {
    statuses = List.copyOf(statuses);
  }

  static EmailSendResult sent(List<MandrillMessageStatus> statuses) {
    return new EmailSendResult(SENT, statuses, null);
  }

  static EmailSendResult failed(List<MandrillMessageStatus> statuses, String problem) {
    return new EmailSendResult(FAILED, statuses, problem);
  }

  static EmailSendResult unknown(List<MandrillMessageStatus> statuses, String problem) {
    return new EmailSendResult(UNKNOWN, statuses, problem);
  }

  public List<MandrillMessageStatus> notAccepted() {
    return statuses.stream().filter(not(MandrillRecipientStatuses::isAccepted)).toList();
  }
}
