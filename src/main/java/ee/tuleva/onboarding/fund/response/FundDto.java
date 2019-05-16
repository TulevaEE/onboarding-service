package ee.tuleva.onboarding.fund.response;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundDto {
    private FundManager fundManager;
    private String isin;
    private String name;
    private BigDecimal managementFeeRate;
    private Integer pillar;
    private BigDecimal ongoingChargesFigure;
    private Fund.FundStatus status;

    public FundDto(Fund fund, String language) {
        this.fundManager = fund.getFundManager();
        this.isin = fund.getIsin();
        this.name = fund.getName(language);
        this.managementFeeRate = fund.getManagementFeeRate();
        this.pillar = fund.getPillar();
        this.ongoingChargesFigure = fund.getOngoingChargesFigure();
        this.status = fund.getStatus();
    }
}