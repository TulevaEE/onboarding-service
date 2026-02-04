package ee.tuleva.onboarding.fund;

import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.StreamSupport.stream;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
  private final LedgerService ledgerService;
  private final SavingsFundConfiguration savingsFundConfiguration;

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
        .map(
            fundValue ->
                PensionFundStatistics.builder()
                    .nav(fundValue.value())
                    .volume(calculateVolume(fund, fundValue.value(), fundValue.date()))
                    .build())
        .orElseGet(PensionFundStatistics::getNull);
  }

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private BigDecimal calculateVolume(Fund fund, BigDecimal nav, LocalDate navDate) {
    if (!savingsFundConfiguration.getIsin().equals(fund.getIsin())) {
      return null;
    }
    var endOfNavDate = navDate.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    BigDecimal outstandingUnits =
        ledgerService
            .getSystemAccount(SystemAccount.FUND_UNITS_OUTSTANDING)
            .getBalanceAt(endOfNavDate);
    return outstandingUnits.multiply(nav).setScale(2, HALF_UP);
  }

  private Iterable<Fund> fundsBy(Optional<String> fundManagerName) {
    return fundManagerName
        .map(fundRepository::findAllByFundManagerNameIgnoreCase)
        .orElseGet(fundRepository::findAll);
  }
}
