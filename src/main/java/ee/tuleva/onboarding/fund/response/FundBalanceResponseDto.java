package ee.tuleva.onboarding.fund.response;

import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FundBalanceResponseDto {
    private FundResponseDto fund;
    private BigDecimal value;
    private String currency;
    private Integer pillar;
    private boolean activeContributions;

    public static FundBalanceResponseDto from(FundBalance fundBalance, String language) {
        return FundBalanceResponseDto.builder()
            .fund(new FundResponseDto(fundBalance.getFund(), language))
            .value(fundBalance.getValue())
            .currency(fundBalance.getCurrency())
            .pillar(fundBalance.getPillar())
            .activeContributions(fundBalance.isActiveContributions())
            .build();
    }

    @Data
    public static class FundResponseDto {
        private FundManager fundManager;
        private String isin;
        private String name;
        private BigDecimal managementFeeRate;
        private Integer pillar;
        private BigDecimal ongoingChargesFigure;
        private Fund.FundStatus status;

        public FundResponseDto(Fund fund, String language) {
            this.fundManager = fund.getFundManager();
            this.isin = fund.getIsin();
            this.name = fund.getName(language);
            this.managementFeeRate = fund.getManagementFeeRate();
            this.pillar = fund.getPillar();
            this.ongoingChargesFigure = fund.getOngoingChargesFigure();
            this.status = fund.getStatus();
        }
    }
}
