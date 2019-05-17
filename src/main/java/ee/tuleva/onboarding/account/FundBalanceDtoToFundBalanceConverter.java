package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.epis.account.FundBalanceDto;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class FundBalanceDtoToFundBalanceConverter implements Converter<FundBalanceDto, FundBalance> {

  private final FundRepository fundRepository;

  @Override
  public FundBalance convert(FundBalanceDto sourceFund) {
    Fund fund = fundRepository.findByIsin(sourceFund.getIsin());

    if (fund == null) {
      throw new IllegalArgumentException("Provided fund isin not found in the database: " + sourceFund);
    }

    return FundBalance.builder()
        .activeContributions(sourceFund.isActiveContributions())
        .currency(sourceFund.getCurrency())
        .pillar(sourceFund.getPillar())
        .value(sourceFund.getValue())
        .units(sourceFund.getUnits())
        .fund(fund)
        .build();

  }
}
