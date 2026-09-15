package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.mandate.MandateType.PAYMENT_RATE_CHANGE;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.mandate.details.MandateDetails;
import ee.tuleva.onboarding.nudge.PaymentRateChangeHistory;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRateChangeMandates implements PaymentRateChangeHistory {

  private final MandateRepository mandateRepository;

  @Override
  public boolean hasChangeSince(User user, Instant since) {
    return mandateRepository
        .findAllByUserIdAndCreatedDateAfter(
            requireNonNull(user.getId(), "User id missing for a payment rate history lookup"),
            since)
        .stream()
        .filter(Mandate::isSigned)
        .map(Mandate::getDetails)
        .filter(Objects::nonNull)
        .map(MandateDetails::getMandateType)
        .anyMatch(PAYMENT_RATE_CHANGE::equals);
  }
}
