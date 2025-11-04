package ee.tuleva.onboarding.notification.email.auto;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.*;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum EmailEvent {
  NEW_LEAVER(SECOND_PILLAR_LEAVERS),
  NEW_EARLY_WITHDRAWAL(SECOND_PILLAR_EARLY_WITHDRAWAL),
  NEW_PAYMENT_RATE_ABANDONMENT(PAYMENT_RATE_ABANDONMENT),
  NEW_SECOND_PILLAR_ABANDONMENT(SECOND_PILLAR_ABANDONMENT),
  ;

  private final EmailType emailType;

  public static EmailEvent getByEmailType(EmailType emailType) {
    return Arrays.stream(EmailEvent.values())
        .filter(emailEvent -> emailEvent.emailType.equals(emailType))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No EmailEvent found: emailType=" + emailType));
  }
}
