package ee.tuleva.onboarding.mandate.email.persistence;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.email.PillarSuggestion;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.user.member.Member;

import java.util.Locale;

public enum EmailType {
  SECOND_PILLAR_MANDATE("second_pillar_mandate"),
  SECOND_PILLAR_WITHDRAWAL_CANCELLATION("second_pillar_withdrawal_cancellation"),
  SECOND_PILLAR_TRANSFER_CANCELLATION("second_pillar_transfer_cancellation"),
  SECOND_PILLAR_PAYMENT_RATE("second_pillar_payment_rate"),
  SECOND_PILLAR_LEAVERS("second_pillar_leavers"),
  SECOND_PILLAR_EARLY_WITHDRAWAL("second_pillar_early_withdrawal"),

  THIRD_PILLAR_SUGGEST_SECOND("third_pillar_suggest_second"),
  THIRD_PILLAR_PAYMENT_REMINDER_MANDATE("third_pillar_payment_reminder_mandate"),
  THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE("third_pillar_payment_success_mandate"),

  MEMBERSHIP("membership"),
  BATCH_FAILED("batch_failed"),

  WITHDRAWAL_BATCH("withdrawal_batch");

  private final String templateName;

  EmailType(String templateName) {
    this.templateName = templateName;
  }

  public static EmailType from(Mandate mandate) {
    if (mandate.isPaymentRateApplication()) {
      return SECOND_PILLAR_PAYMENT_RATE;
    }
    if (mandate.isWithdrawalCancellation() || mandate.isEarlyWithdrawalCancellation()) {
      return SECOND_PILLAR_WITHDRAWAL_CANCELLATION;
    }
    if (mandate.isTransferCancellation()) {
      return SECOND_PILLAR_TRANSFER_CANCELLATION;
    }
    if (mandate.isThirdPillar()) {
      return THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;
    }
    return SECOND_PILLAR_MANDATE;
  }

  public static EmailType from(MandateBatch batch) {
    var allMandatesWithdrawals =
        batch.getMandates().stream()
            .allMatch(
                mandate ->
                    mandate.getMandateType() == MandateType.PARTIAL_WITHDRAWAL
                        || mandate.getMandateType() == MandateType.FUND_PENSION_OPENING);

    if (allMandatesWithdrawals) {
      return WITHDRAWAL_BATCH;
    }

    throw new IllegalArgumentException("Cannot find email type for batch");
  }

  public static EmailType from(Mandate mandate, PillarSuggestion pillarSuggestion) {
    if (mandate.isThirdPillar() && pillarSuggestion.isSuggestSecondPillar()) {
      return THIRD_PILLAR_SUGGEST_SECOND;
    }
    return THIRD_PILLAR_SUGGEST_SECOND;
  }

  public static EmailType from(Payment payment) {
    return THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE;
  }

  public static EmailType from(Member member) {
    return MEMBERSHIP;
  }

  public String getTemplateName(Locale locale) {
    return templateName + "_" + locale.getLanguage();
  }
}
