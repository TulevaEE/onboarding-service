package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KPRUnitsOsakudToFundBalanceConverter implements Converter<PensionAccountBalanceResponseType.Units.Balance, FundBalance> {

    private final FundRepository fundRepository;

    @Override
    public FundBalance convert(PensionAccountBalanceResponseType.Units.Balance source) {

        Fund fund = fundRepository.findByNameIgnoreCase(source.getSecurityName());
        if(fund == null) {
            throw new RuntimeException("Unable to find fund by name!");
        }

        return FundBalance.builder()
                .fund(fund)
                .value(source.getAmount().multiply(source.getNav()))
                .currency(source.getCurrency())
                .build();

    }
}
