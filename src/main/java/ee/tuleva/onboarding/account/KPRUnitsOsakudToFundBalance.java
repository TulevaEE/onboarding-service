package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import org.springframework.core.convert.converter.Converter;


public class KPRUnitsOsakudToFundBalance implements Converter<PensionAccountBalanceResponseType.Units.Balance, FundBalance> {

    public FundBalance convert(PensionAccountBalanceResponseType.Units.Balance source) {
        return FundBalance.builder()
                .isin("") // todo matching from securityName, no key from API?
                .name(source.getSecurityName())
                .manager(null) // todo should be mapped by us.
                //source.getNav();
                .price(source.getAmount())
                .currency(source.getCurrency())
                .build();
    }

}
