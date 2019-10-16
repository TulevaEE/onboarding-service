package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
@AllArgsConstructor
public class FundBalanceDtoToFundBalanceConverter implements Converter<FundBalanceDto, FundBalance> {

    private final FundRepository fundRepository;
    private final CashFlowService cashFlowService;

    @NonNull
    public FundBalance convert(FundBalanceDto fundBalanceDto, Person person) {
        FundBalance fundBalance = convert(fundBalanceDto);
        fundBalance.setContributionSum(calculateContributionSum(fundBalanceDto.getIsin(), person));
        return fundBalance;
    }

    @Override
    @NonNull
    public FundBalance convert(FundBalanceDto fundBalanceDto) {
        Fund fund = fundRepository.findByIsin(fundBalanceDto.getIsin());

        if (fund == null) {
            throw new IllegalArgumentException("Provided fund isin not found in the database: " + fundBalanceDto);
        }

        return FundBalance.builder()
            .activeContributions(fundBalanceDto.isActiveContributions())
            .currency(fundBalanceDto.getCurrency())
            .pillar(fundBalanceDto.getPillar())
            .value(fundBalanceDto.getValue())
            .unavailableValue(fundBalanceDto.getUnavailableValue())
            .units(fundBalanceDto.getUnits())
            .fund(fund)
            .build();
    }

    private BigDecimal calculateContributionSum(String isin, Person person) {
        return cashFlowService.getCashFlowStatement(person)
            .getTransactions()
            .stream()
            .filter(cashFlow -> isin.equalsIgnoreCase(cashFlow.getIsin()))
            .map(CashFlow::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
