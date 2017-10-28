package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.epis.account.FundBalance;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class FundBalanceDTOToFundBalanceConverter implements Converter<FundBalanceDTO, FundBalance>{

    private final FundRepository fundRepository;

    @Override
    public FundBalance convert(FundBalanceDTO source) {
        Fund fund = fundRepository.findByIsin(source.getIsin());

        if (fund == null) {
            throw new IllegalArgumentException("Provided Fund isin is not found in the database " + source.getIsin());
        }

        return FundBalance.builder()
                .activeContributions(source.isActiveContributions())
                .currency(source.getCurrency())
                .pillar(source.getPillar())
                .value(source.getValue())
                .fund(fund)
                .build();

    }
}
