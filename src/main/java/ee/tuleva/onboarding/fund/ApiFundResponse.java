package ee.tuleva.onboarding.fund;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.fund.Fund.FundStatus;
import ee.tuleva.onboarding.fund.manager.FundManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiFundResponse {
  private FundManager fundManager;
  private String isin;
  private String name;
  private BigDecimal managementFeeRate;
  private Integer pillar;
  private BigDecimal ongoingChargesFigure;
  private FundStatus status;
  private LocalDate inceptionDate;

  public ApiFundResponse(Fund fund, Locale locale) {
    this.fundManager = fund.getFundManager();
    this.isin = fund.getIsin();
    this.name = fund.getName(locale);
    this.managementFeeRate = fund.getManagementFeeRate();
    this.pillar = fund.getPillar();
    this.ongoingChargesFigure = fund.getOngoingChargesFigure();
    this.status = fund.getStatus();
    this.inceptionDate = fund.getInceptionDate();
  }

  @JsonIgnore
  public boolean isOwnFund() {
    return fundManager.isTuleva();
  }
}
