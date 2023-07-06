package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Exchange {
  private ApiFundResponse sourceFund;
  private ApiFundResponse targetFund;
  private String targetPik;
  private BigDecimal amount;

  public Exchange(
      ApiFundResponse sourceFund, ApiFundResponse targetFund, String targetPik, BigDecimal amount) {
    if (targetFund == null && targetPik == null) {
      throw new IllegalArgumentException("Target fund or target PIK needs to be defined");
    }
    if (targetFund != null && targetPik != null) {
      throw new IllegalArgumentException(
          "Both target fund and target PIK can not be present at the same time");
    }

    this.sourceFund = sourceFund;
    this.targetFund = targetFund;
    this.targetPik = targetPik;
    this.amount = amount;
  }

  @JsonIgnore
  public Integer getPillar() {
    Integer sourcePillar = sourceFund.getPillar();
    Integer targetPillar = getTargetPillar();

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

  private Integer getTargetPillar() {
    return targetPik != null ? 2 : targetFund.getPillar();
  }

  @JsonIgnore
  public boolean isFullAmount() {
    return getPillar() == 2 && amount.intValue() == 1; // 100%
  }
}
