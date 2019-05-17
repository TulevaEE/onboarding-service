package ee.tuleva.onboarding.fund.response;

import ee.tuleva.onboarding.account.FundBalance;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FundBalanceResponseDto {
    private FundDto fund;
    private BigDecimal value;
    private String currency;
    private Integer pillar;
    private boolean activeContributions;
    private BigDecimal contributionSum;

    public static FundBalanceResponseDto from(FundBalance fundBalance, String language) {
        return FundBalanceResponseDto.builder()
            .fund(new FundDto(fundBalance.getFund(), language))
            .value(fundBalance.getValue())
            .currency(fundBalance.getCurrency())
            .pillar(fundBalance.getPillar())
            .activeContributions(fundBalance.isActiveContributions())
            .contributionSum(fundBalance.getContributionSum())
            .build();
    }
}
