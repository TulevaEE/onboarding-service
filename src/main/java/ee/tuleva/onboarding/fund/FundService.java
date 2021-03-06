package ee.tuleva.onboarding.fund;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import ee.tuleva.onboarding.fund.response.FundResponse;
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
public class FundService {

  private final FundRepository fundRepository;
  private final PensionFundStatisticsService pensionFundStatisticsService;
  private final LocaleService localeService;

  public List<FundResponse> getFunds(Optional<String> fundManagerName) {
    return stream(fundsBy(fundManagerName).spliterator(), false)
        .sorted()
        .map(fund -> new FundResponse(fund, getStatistics(fund), localeService.getLanguage()))
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
