package ee.tuleva.onboarding.mandate.application;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
@Builder
public class Exchange {
  private ApiFundResponse sourceFund;
  private @Nullable ApiFundResponse targetFund;
  private @Nullable String targetPik;

  /*
   * 2nd pillar: Fraction of bookValue (i.e. min 0, max 1).
   * 3rd pillar: Number of bookValue (i.e. min 0, max number of bookValue you have)
   */
  private @Nullable BigDecimal amount;

  public Exchange(
      ApiFundResponse sourceFund,
      @Nullable ApiFundResponse targetFund,
      @Nullable String targetPik,
      @Nullable BigDecimal amount) {
    if (targetFund == null && targetPik == null) {
      throw new IllegalArgumentException("Target fund or target PIK needs to be defined");
    }
    if (targetFund != null && targetPik != null) {
      throw new IllegalArgumentException(
          "Both target fund and target PIK can not be present at the same time");
    }
    Integer pillar = getPillar(sourceFund, targetFund, targetPik);
    boolean amountBetween0and1 =
        amount != null && amount.compareTo(ZERO) > 0 && amount.compareTo(ONE) <= 0;
    if (pillar == 2 && !amountBetween0and1) {
      throw new IllegalArgumentException(
          "2nd pillar exchange amount has to be between 0 and 1.0 (which means between 0% and 100%)");
    }

    this.sourceFund = sourceFund;
    this.targetFund = targetFund;
    this.targetPik = targetPik;
    this.amount = amount;
  }

  @JsonIgnore
  public Integer getPillar() {
    return getPillar(sourceFund, targetFund, targetPik);
  }

  public Integer getPillar(
      ApiFundResponse sourceFund,
      @Nullable ApiFundResponse targetFund,
      @Nullable String targetPik) {
    Integer sourcePillar = sourceFund.getPillar();
    Integer targetPillar =
        targetPik != null
            ? 2
            : requireNonNull(targetFund, "Target fund or target PIK needs to be defined")
                .getPillar();

    if (sourcePillar.equals(targetPillar)) {
      return sourcePillar;
    }
    throw new IllegalStateException("Transfer between different pillar funds");
  }

  @JsonIgnore
  public boolean isFromOwnFund() {
    return sourceFund != null && sourceFund.isOwnFund();
  }

  @JsonIgnore
  public boolean isToOwnFund() {
    return targetFund != null && targetFund.isOwnFund();
  }

  @JsonIgnore
  public boolean isFullAmount() {
    if (getPillar() != 2) {
      throw new IllegalStateException("isFullAmount() is only supported for 2nd pillar");
    }
    return requireAmount().intValue() == 1; // 100% of bookValue
  }

  @JsonIgnore
  public boolean isFullAmount(BigDecimal fundBalanceUnits) {
    if (getPillar() != 3) {
      throw new IllegalStateException(
          "isFullAmount(fundBalanceUnits) is only supported for 3rd pillar");
    }
    return requireAmount().compareTo(fundBalanceUnits) == 0;
  }

  @JsonIgnore
  public BigDecimal getValue(BigDecimal totalValue, BigDecimal totalUnits) {
    if (getPillar() == 2) {
      return requireAmount().multiply(totalValue);
    }
    if (getPillar() == 3) {
      return ZERO.compareTo(totalUnits) == 0
          ? ZERO
          : requireAmount().multiply(totalValue).divide(totalUnits, 2, RoundingMode.HALF_UP);
    }
    throw new IllegalStateException("Unknown pillar: " + getPillar());
  }

  private BigDecimal requireAmount() {
    return requireNonNull(amount, "Exchange amount missing: sourceIsin=" + getSourceIsin());
  }

  @JsonIgnore
  public String getSourceIsin() {
    return sourceFund.getIsin();
  }

  @JsonIgnore
  public String getTargetIsin() {
    return requireNonNull(targetFund, "Target fund missing: sourceIsin=" + getSourceIsin())
        .getIsin();
  }

  @JsonIgnore
  public BigDecimal getSourceFundFees() {
    return sourceFund.getOngoingChargesFigure();
  }

  @JsonIgnore
  public BigDecimal getTargetFundFees() {
    return requireNonNull(targetFund, "Target fund missing: sourceIsin=" + getSourceIsin())
        .getOngoingChargesFigure();
  }

  @JsonIgnore
  public boolean isToPik() {
    return targetPik != null;
  }
}
