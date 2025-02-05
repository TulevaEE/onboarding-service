package ee.tuleva.onboarding.fund;

import static java.util.stream.StreamSupport.stream;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.locale.LocaleService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
class FundService {

  private final FundRepository fundRepository;
  private final PensionFundStatisticsService pensionFundStatisticsService;
  private final FundValueRepository fundValueRepository;
  private final LocaleService localeService;

  List<ExtendedApiFundResponse> getFunds(Optional<String> fundManagerName) {
    return stream(fundsBy(fundManagerName).spliterator(), false)
        .sorted()
        .map(
            fund ->
                new ExtendedApiFundResponse(
                    fund, getStatistics(fund), localeService.getCurrentLocale()))
        .toList();
  }

  private PensionFundStatistics getStatistics(Fund fund) {
    List<PensionFundStatistics> statistics = pensionFundStatisticsService.getCachedStatistics();
    return statistics.stream()
        .filter(statistic -> Objects.equals(statistic.getIsin(), fund.getIsin()))
        .findFirst()
        .orElseGet(() -> fallbackNavStatistics(fund));
  }

  private PensionFundStatistics fallbackNavStatistics(Fund fund) {
    return fundValueRepository
        .findLastValueForFund(fund.getIsin())
        .map(fundValue -> PensionFundStatistics.builder().nav(fundValue.getValue()).build())
        .orElseGet(PensionFundStatistics::getNull);
  }

  private Iterable<Fund> fundsBy(Optional<String> fundManagerName) {
    return fundManagerName
        .map(fundRepository::findAllByFundManagerNameIgnoreCase)
        .orElseGet(fundRepository::findAll);
  }
}
