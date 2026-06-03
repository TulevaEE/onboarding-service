package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.TKF_VOLUME_15K_NEW_CLIENT;
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.TKF_VOLUME_30K_EXISTING_CLIENT;
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.TKF_VOLUME_49K_YEARLY;
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.COMBINED;
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.IN;
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.OUT;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mirrors the live TKF volume triggers in T1_ulevaatamist_vajavad.sql (issue #55, sisekord
 * 02.06.2026): #4 15k deposits/month for new clients (>=), #5 30k deposits OR redemptions/month for
 * existing clients evaluated per-direction (>=), #6 49k deposits/calendar-year for all clients
 * (strict >). An alert is suppressed when the client was manually reviewed
 * (TKF_RISK_LEVEL_OVERRIDE) after the breaching activity.
 */
@Component
public class TkfVolumeEvaluator {

  private static final BigDecimal NEW_CLIENT_MONTHLY_THRESHOLD = new BigDecimal("15000");
  private static final BigDecimal EXISTING_CLIENT_MONTHLY_THRESHOLD = new BigDecimal("30000");
  private static final BigDecimal YEARLY_DEPOSIT_THRESHOLD = new BigDecimal("49000");

  public List<TkfVolumeAlert> evaluate(TkfVolumeAggregate aggregate) {
    var alerts = new ArrayList<TkfVolumeAlert>();
    boolean depositReviewed =
        reviewedAfter(aggregate.lastManualReview(), aggregate.lastDepositThisMonth());
    boolean redemptionReviewed =
        reviewedAfter(aggregate.lastManualReview(), aggregate.lastRedemptionThisMonth());
    boolean yearlyReviewed =
        reviewedAfter(aggregate.lastManualReview(), aggregate.lastDepositThisYear());

    if (aggregate.presentInCrm()
        && !aggregate.existingClient()
        && atLeast(aggregate.depositsThisMonth(), NEW_CLIENT_MONTHLY_THRESHOLD)
        && !depositReviewed) {
      alerts.add(
          new TkfVolumeAlert(
              TKF_VOLUME_15K_NEW_CLIENT, IN, aggregate.depositsThisMonth(), aggregate.monthKey()));
    }
    if (aggregate.presentInCrm()
        && aggregate.existingClient()
        && atLeast(aggregate.depositsThisMonth(), EXISTING_CLIENT_MONTHLY_THRESHOLD)
        && !depositReviewed) {
      alerts.add(
          new TkfVolumeAlert(
              TKF_VOLUME_30K_EXISTING_CLIENT,
              IN,
              aggregate.depositsThisMonth(),
              aggregate.monthKey()));
    }
    if (aggregate.presentInCrm()
        && aggregate.existingClient()
        && atLeast(aggregate.redemptionsThisMonth(), EXISTING_CLIENT_MONTHLY_THRESHOLD)
        && !redemptionReviewed) {
      alerts.add(
          new TkfVolumeAlert(
              TKF_VOLUME_30K_EXISTING_CLIENT,
              OUT,
              aggregate.redemptionsThisMonth(),
              aggregate.monthKey()));
    }
    if (greaterThan(aggregate.depositsThisYear(), YEARLY_DEPOSIT_THRESHOLD) && !yearlyReviewed) {
      alerts.add(
          new TkfVolumeAlert(
              TKF_VOLUME_49K_YEARLY, COMBINED, aggregate.depositsThisYear(), aggregate.yearKey()));
    }
    return alerts;
  }

  private static boolean reviewedAfter(Instant lastManualReview, Instant breachingActivity) {
    return lastManualReview != null
        && breachingActivity != null
        && !breachingActivity.isAfter(lastManualReview);
  }

  private static boolean atLeast(BigDecimal amount, BigDecimal threshold) {
    return amount.compareTo(threshold) >= 0;
  }

  private static boolean greaterThan(BigDecimal amount, BigDecimal threshold) {
    return amount.compareTo(threshold) > 0;
  }
}
