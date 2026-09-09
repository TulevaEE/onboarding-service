package ee.tuleva.onboarding.nudge;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static ee.tuleva.onboarding.nudge.NudgeKey.ACCOUNT_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.MEMBERSHIP;
import static ee.tuleva.onboarding.nudge.NudgeKey.SAVINGS_FUND;
import static ee.tuleva.onboarding.nudge.NudgeKey.SAVINGS_FUND_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_PAYMENT_RATE;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_FEES;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RAISE;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_START;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(NON_NULL)
@JsonPropertyOrder({"key", "tag", "feeComparison", "savingsFundFeePercent"})
public record NudgeDecision(
    NudgeKey key,
    @Nullable FeeComparison feeComparison,
    @Nullable BigDecimal savingsFundFeePercent) {

  public static NudgeDecision of(NudgeKey key) {
    return new NudgeDecision(key, null, null);
  }

  public static NudgeDecision secondPillarTransfer(@Nullable FeeComparison feeComparison) {
    return new NudgeDecision(SECOND_PILLAR_TRANSFER, feeComparison, null);
  }

  public static NudgeDecision savingsFund(BigDecimal feePercent) {
    return new NudgeDecision(SAVINGS_FUND, null, feePercent);
  }

  @JsonProperty
  public String tag() {
    return key.getTag();
  }

  public Map<String, Object> mergeVars(Locale locale) {
    Map<String, Object> vars = new HashMap<>();
    vars.put("suggestSecondPillar", key == SECOND_PILLAR_TRANSFER);
    vars.put("suggestPaymentRate", key == SECOND_PILLAR_PAYMENT_RATE);
    vars.put("suggestThirdPillar", key == THIRD_PILLAR_START || key == THIRD_PILLAR_FEES);
    vars.put("thirdPillarActive", key == THIRD_PILLAR_FEES);
    vars.put("suggestThirdPillarRecurringPayment", key == THIRD_PILLAR_RECURRING);
    vars.put("suggestThirdPillarRaise", key == THIRD_PILLAR_RAISE);
    vars.put("suggestSavingsFund", key == SAVINGS_FUND);
    vars.put(
        "suggestSavingsFundRecurringPayment",
        key == SAVINGS_FUND_RECURRING || key == ACCOUNT_RECURRING);
    vars.put("suggestMembership", key == MEMBERSHIP);
    vars.put("hasFeeComparison", feeComparison != null);
    if (feeComparison != null) {
      vars.put("secondPillarFeePercent", percent(feeComparison.currentFeePercent(), locale));
      vars.put("secondPillarFeeAmount", feeComparison.currentFeeAmount());
      vars.put("tulevaFeeAmount", feeComparison.tulevaFeeAmount());
      vars.put("secondPillarSavingsAmount", feeComparison.savingsAmount());
    }
    if (savingsFundFeePercent != null) {
      vars.put("savingsFundFee", percent(savingsFundFeePercent, locale));
    }
    return vars;
  }

  private static String percent(BigDecimal percent, Locale locale) {
    String plain = percent.stripTrailingZeros().toPlainString();
    return "et".equals(locale.getLanguage()) ? plain.replace('.', ',') : plain;
  }
}
