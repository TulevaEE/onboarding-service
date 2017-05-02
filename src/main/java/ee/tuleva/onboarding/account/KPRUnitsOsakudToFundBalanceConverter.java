package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KPRUnitsOsakudToFundBalanceConverter implements Converter<PensionAccountBalanceResponseType.Units.Balance, FundBalance> {

    private final FundRepository fundRepository;

    @Override
    public FundBalance convert(PensionAccountBalanceResponseType.Units.Balance source) {

        String fundName = removeBookedFundSuffix(source.getSecurityName());

        Fund fund = fundRepository.findByNameIgnoreCase(fundName);
        if (fund == null) {
            throw new RuntimeException("Unable to find fund by name! " + fundName);
        }

        return FundBalance.builder()
                .fund(fund)
                .value(source.getAmount().multiply(source.getNav()))
                .currency(source.getCurrency())
                .build();
    }

    private String removeBookedFundSuffix(String fundName) {
        int suffixStart = fundName.indexOf(" - Broneeritud");

        if(suffixStart != -1 ) {
            return fundName.substring(0, suffixStart);
        } else {
            return fundName;
        }
    }
}
