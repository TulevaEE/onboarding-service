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
    private Integer pillar;
    private BigDecimal ongoingChargesFigure;
    private Fund.FundStatus status;


    public FundResponse(Fund fund, PensionFundStatistics pensionFundStatistics, String language) {
        this.fundManager = fund.getFundManager();
        this.isin = fund.getIsin();
        this.name = "et".equalsIgnoreCase(language) ? fund.getNameEstonian() : fund.getNameEnglish();
        this.managementFeeRate = fund.getManagementFeeRate();
        this.nav = pensionFundStatistics.getNav();
        this.volume = pensionFundStatistics.getVolume();
        this.pillar = fund.getPillar();
        this.ongoingChargesFigure = fund.getOngoingChargesFigure();
        this.status = fund.getStatus();
    }
}
