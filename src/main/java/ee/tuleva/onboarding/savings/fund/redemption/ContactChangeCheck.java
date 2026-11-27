package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.CONTACT_CHANGED;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal;
import ee.tuleva.onboarding.user.ReplacedContacts;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ContactChangeCheck implements TakeoverSignalCheck {

  private static final Duration CHANGED_IF_REPLACED_WITHIN = Duration.ofHours(72);

  private final ReplacedContacts replacedContacts;

  @Override
  public Optional<TakeoverSignal> raisedBy(RedemptionRequest request) {
    Instant requestedAt = request.getRequestedAt();
    return replacedContacts.anyBetween(
            request.getUserId(), requestedAt.minus(CHANGED_IF_REPLACED_WITHIN), requestedAt)
        ? Optional.of(CONTACT_CHANGED)
        : Optional.empty();
  }
}
