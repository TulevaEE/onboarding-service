package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Onboarding.INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.CLEAR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.FREEZE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.HOLD_PAYOUT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.RETRY;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.HIGH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.PEP_HIT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.SANCTION_HIT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.UNAVAILABLE;
import static java.util.stream.Collectors.toCollection;

import ee.tuleva.onboarding.aml.ScreeningOutcome;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class RedemptionVerdict {

  public enum Outcome {
    RETRY,
    FREEZE,
    HOLD_PAYOUT,
    CLEAR
  }

  public enum Screening {
    CLEAR,
    PEP_HIT,
    SANCTION_HIT,
    UNAVAILABLE,
    FAILED,
    NOT_REPEATED;

    static Screening of(ScreeningOutcome outcome) {
      return switch (outcome) {
        case CLEAR -> CLEAR;
        case PEP_HIT -> PEP_HIT;
        case SANCTION_HIT -> SANCTION_HIT;
        case UNAVAILABLE -> UNAVAILABLE;
      };
    }

    boolean leavesTheRiskLevelUnasked() {
      return this == UNAVAILABLE || this == SANCTION_HIT;
    }
  }

  public enum Risk {
    HIGH,
    NOT_HIGH,
    UNAVAILABLE,
    NOT_ASKED
  }

  public enum Onboarding {
    COMPLETED,
    INCOMPLETE,
    NOT_CHECKED
  }

  public record Evidence(Screening screening, Risk risk, Onboarding onboarding) {}

  private static final Map<RedemptionHoldReason, Predicate<Evidence>> HOLD_REASONS =
      new EnumMap<>(
          Map.of(
              RedemptionHoldReason.SCREENING_UNAVAILABLE, RedemptionVerdict::screeningUnavailable,
              RedemptionHoldReason.PEP, RedemptionVerdict::pep,
              RedemptionHoldReason.KYB_SCREENING_FAILED, RedemptionVerdict::kybScreeningFailed,
              RedemptionHoldReason.ONBOARDING_INCOMPLETE, RedemptionVerdict::onboardingIncomplete,
              RedemptionHoldReason.HIGH_RISK, RedemptionVerdict::highRisk,
              RedemptionHoldReason.RISK_LEVEL_UNAVAILABLE,
                  RedemptionVerdict::riskLevelUnavailable));

  private RedemptionVerdict() {}

  public static Outcome outcome(Evidence evidence) {
    if (screeningUnavailable(evidence)) {
      return RETRY;
    }
    if (evidence.screening() == SANCTION_HIT) {
      return FREEZE;
    }
    if (riskLevelUnavailable(evidence)
        && !pep(evidence)
        && !kybScreeningFailed(evidence)
        && !onboardingIncomplete(evidence)) {
      return RETRY;
    }
    return anyHoldReason(evidence) ? HOLD_PAYOUT : CLEAR;
  }

  public static Set<RedemptionHoldReason> holdReasons(Evidence evidence) {
    return HOLD_REASONS.entrySet().stream()
        .filter(reason -> reason.getValue().test(evidence))
        .map(Map.Entry::getKey)
        .collect(toCollection(() -> EnumSet.noneOf(RedemptionHoldReason.class)));
  }

  private static boolean anyHoldReason(Evidence evidence) {
    return pep(evidence)
        || kybScreeningFailed(evidence)
        || onboardingIncomplete(evidence)
        || highRisk(evidence)
        || riskLevelUnavailable(evidence);
  }

  private static boolean screeningUnavailable(Evidence evidence) {
    return evidence.screening() == UNAVAILABLE;
  }

  private static boolean pep(Evidence evidence) {
    return evidence.screening() == PEP_HIT;
  }

  private static boolean kybScreeningFailed(Evidence evidence) {
    return evidence.screening() == FAILED;
  }

  private static boolean onboardingIncomplete(Evidence evidence) {
    return evidence.screening() == Screening.CLEAR && evidence.onboarding() == INCOMPLETE;
  }

  private static boolean highRisk(Evidence evidence) {
    return evidence.risk() == HIGH;
  }

  private static boolean riskLevelUnavailable(Evidence evidence) {
    return evidence.risk() == Risk.UNAVAILABLE;
  }
}
