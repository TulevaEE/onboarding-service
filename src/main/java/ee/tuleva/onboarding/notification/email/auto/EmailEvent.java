package ee.tuleva.onboarding.notification.email.auto;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum EmailEvent {
  NEW_LEAVER(SECOND_PILLAR_LEAVERS),
  NEW_EARLY_WITHDRAWAL(SECOND_PILLAR_EARLY_WITHDRAWAL);

  private final EmailType emailType;

  public static EmailEvent getByEmailType(EmailType emailType) {
    return Arrays.stream(EmailEvent.values())
        .filter(emailEvent -> emailEvent.emailType.equals(emailType))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No EmailEvent found: emailType=" + emailType));
  }
}
