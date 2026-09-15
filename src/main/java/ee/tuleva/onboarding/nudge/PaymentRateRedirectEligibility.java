package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class PaymentRateRedirectEligibility {

  private static final int STARTING_RATE = 2;

  private final NudgeDecisionService nudgeDecisionService;
  private final SecondPillarPaymentRateService paymentRateService;
  private final PaymentRateChangeHistory paymentRateChangeHistory;
  private final MonthlySalary monthlySalary;
  private final PaymentRateSeasons paymentRateSeasons;
  private final PaymentRateRedirectProperties properties;

  boolean isEligible(User user) {
    try {
      return savesTheStartingRateAtTuleva(user)
          && !paymentRateChangeHistory.hasChangeSince(user, paymentRateSeasons.previousDeadline())
          && earnsEnough(user);
    } catch (RuntimeException e) {
      log.warn(
          "Payment rate redirect inputs unavailable, treating as not eligible: userId={}",
          user.getId(),
          e);
      return false;
    }
  }

  private boolean savesTheStartingRateAtTuleva(User user) {
    NudgeInputs inputs = nudgeDecisionService.inputsFor(user);
    return inputs.adult()
        && !inputs.reachedRetirementAge()
        && inputs.secondPillarActive()
        && inputs.secondPillarFullyConverted()
        && inputs.leftSecondPillar().isNo()
        && !inputs.pendingSecondPillarWithdrawal()
        && paysTheStartingRate(paymentRateService.getPaymentRates(user));
  }

  private static boolean paysTheStartingRate(PaymentRates rates) {
    return Integer.valueOf(STARTING_RATE).equals(rates.getCurrent())
        && rates.getPending().orElse(STARTING_RATE) <= STARTING_RATE;
  }

  private boolean earnsEnough(User user) {
    BigDecimal threshold = properties.salaryThreshold();
    return monthlySalary
        .latestGross(user, STARTING_RATE)
        .filter(gross -> gross.compareTo(threshold) >= 0)
        .isPresent();
  }
}
