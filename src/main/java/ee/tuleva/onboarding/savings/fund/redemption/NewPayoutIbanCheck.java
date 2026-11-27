package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.iban.IbanValidator.canonicalize;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.NEW_IBAN;
import static java.util.Comparator.naturalOrder;
import static java.util.function.BinaryOperator.minBy;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.savings.fund.OwnDepositHistory;
import ee.tuleva.onboarding.savings.fund.OwnDepositHistory.FirstDeposit;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NewPayoutIbanCheck implements TakeoverSignalCheck {

  private static final Duration NEW_IF_FIRST_PAID_IN_FROM_WITHIN = Duration.ofDays(30);

  private final OwnDepositHistory ownDepositHistory;

  @Override
  public Optional<TakeoverSignal> raisedBy(RedemptionRequest request) {
    return isNewPayoutIban(request) ? Optional.of(NEW_IBAN) : Optional.empty();
  }

  private boolean isNewPayoutIban(RedemptionRequest request) {
    Map<String, Instant> firstDepositByIban = firstDepositByIbanUntil(request);
    Instant newSince = request.getRequestedAt().minus(NEW_IF_FIRST_PAID_IN_FROM_WITHIN);
    return Optional.ofNullable(firstDepositByIban.get(request.getCustomerIban()))
        .filter(firstDeposit -> !firstDeposit.isBefore(newSince))
        .filter(
            firstDeposit ->
                firstDepositByIban.values().stream()
                    .anyMatch(other -> other.isBefore(firstDeposit)))
        .isPresent();
  }

  private Map<String, Instant> firstDepositByIbanUntil(RedemptionRequest request) {
    return ownDepositHistory
        .firstDepositPerRemitterIban(request.getPartyId(), request.getRequestedAt())
        .stream()
        .collect(
            toMap(
                deposit -> canonicalize(deposit.remitterIban()),
                FirstDeposit::recordedAt,
                minBy(naturalOrder())));
  }
}
