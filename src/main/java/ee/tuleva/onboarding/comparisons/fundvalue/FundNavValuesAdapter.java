package ee.tuleva.onboarding.comparisons.fundvalue;

import ee.tuleva.onboarding.fund.FundNavValues;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundNavValuesAdapter implements FundNavValues {

  private final FundValueQueries fundValueQueries;

  @Override
  public Optional<NavPoint> latestValueOnOrBefore(String isin, LocalDate date) {
    return fundValueQueries.getLatestValue(isin, date).map(FundNavValuesAdapter::toNavPoint);
  }

  @Override
  public Optional<NavPoint> lastValue(String isin) {
    return fundValueQueries.findLastValueForFund(isin).map(FundNavValuesAdapter::toNavPoint);
  }

  @Override
  public List<NavPoint> valuesBetween(String isin, LocalDate start, LocalDate end) {
    return fundValueQueries.findValuesBetweenDates(isin, start, end).stream()
        .map(FundNavValuesAdapter::toNavPoint)
        .toList();
  }

  private static NavPoint toNavPoint(FundValue fundValue) {
    return new NavPoint(fundValue.date(), fundValue.value());
  }
}
