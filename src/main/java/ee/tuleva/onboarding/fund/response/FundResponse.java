package ee.tuleva.onboarding.fund.response;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class FundResponse {

  private FundManager fundManager;
  private String isin;
  private String name;
  private BigDecimal managementFeeRate;
  private BigDecimal nav;
  private BigDecimal volume;

  public FundResponse(Fund fund, PensionFundStatistics pensionFundStatistics) {
    this.fundManager = fund.getFundManager();
    this.isin = fund.getIsin();
    this.name = fund.getName();
    this.managementFeeRate = fund.getManagementFeeRate();
    this.nav = pensionFundStatistics.getNav();
    this.volume = pensionFundStatistics.getVolume();
  }

}
