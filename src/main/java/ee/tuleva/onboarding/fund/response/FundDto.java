package ee.tuleva.onboarding.fund.response;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.Fund.FundStatus;
import ee.tuleva.onboarding.fund.manager.FundManager;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class FundDto {
  private FundManager fundManager;
  private String isin;
  private String name;
  private BigDecimal managementFeeRate;
  private Integer pillar;
  private BigDecimal ongoingChargesFigure;
  private FundStatus status;

  public FundDto(Fund fund, String language) {
    this.fundManager = fund.getFundManager();
    this.isin = fund.getIsin();
    this.name = fund.getName(language);
    this.managementFeeRate = fund.getManagementFeeRate();
    this.pillar = fund.getPillar();
    this.ongoingChargesFigure = fund.getOngoingChargesFigure();
    this.status = fund.getStatus();
  }

  public boolean isConverted() {
    return fundManager.isTuleva();
  }
}
