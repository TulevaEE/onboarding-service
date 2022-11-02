package ee.tuleva.onboarding.fund;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.locale.LocaleService;

import java.math.BigDecimal;
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
  private final LocaleService localeService;

  List<ExtendedApiFundResponse> getFunds(Optional<String> fundManagerName) {
    return stream(fundsBy(fundManagerName).spliterator(), false)
        .sorted()
        .map(
            fund ->
                new ExtendedApiFundResponse(
                    fund, new PensionFundStatistics(
                        fund.getIsin(), BigDecimal.valueOf(10000), BigDecimal.valueOf(40), 1000
                ), localeService.getCurrentLocale()))
        .collect(toList());
  }

  private PensionFundStatistics getStatistics(Fund fund) {
    return pensionFundStatisticsService.getCachedStatistics().stream()
        .filter(statistic -> Objects.equals(statistic.getIsin(), fund.getIsin()))
        .findFirst()
        .orElse(PensionFundStatistics.getNull());
  }

  private Iterable<Fund> fundsBy(Optional<String> fundManagerName) {
    return fundManagerName
        .map(fundRepository::findAllByFundManagerNameIgnoreCase)
        .orElse(fundRepository.findAll());
  }
}
