package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.StreamSupport.stream;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import java.math.BigDecimal;
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
  private final SavingsFundNavProvider savingsFundNavProvider;

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
    boolean isSavingsFund = savingsFundConfiguration.getIsin().equals(fund.getIsin());
    Optional<FundValue> latestValue =
        isSavingsFund
            ? fundValueRepository.getLatestValue(
                fund.getIsin(), savingsFundNavProvider.safeMaxNavDate())
            : fundValueRepository.findLastValueForFund(fund.getIsin());
    return latestValue
        .map(fundValue -> buildSavingsFundStatistics(fund, fundValue))
        .orElseGet(PensionFundStatistics::getNull);
  }

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private PensionFundStatistics buildSavingsFundStatistics(Fund fund, FundValue latestFundValue) {
    if (!savingsFundConfiguration.getIsin().equals(fund.getIsin())) {
      return PensionFundStatistics.builder().nav(latestFundValue.value()).build();
    }

    var account = ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
    var currentBalance = account.getBalance();
    var cutoff = latestFundValue.date().plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    var balanceAtCutoff = account.getBalanceAt(cutoff);
    var peopleCount = ledgerService.countAccountsWithPositiveBalance(FUND_UNITS);

    boolean issuanceCompleted = currentBalance.compareTo(balanceAtCutoff) != 0;

    if (issuanceCompleted) {
      var nav = toNavScale(latestFundValue.value());
      return PensionFundStatistics.builder()
          .nav(nav)
          .volume(currentBalance.multiply(nav).setScale(2, HALF_UP))
          .activeCount(peopleCount)
          .build();
    }

    var previousNav =
        toNavScale(
            fundValueRepository
                .getLatestValue(fund.getIsin(), latestFundValue.date().minusDays(1))
                .map(FundValue::value)
                .orElse(latestFundValue.value()));
    return PensionFundStatistics.builder()
        .nav(previousNav)
        .volume(currentBalance.multiply(previousNav).setScale(2, HALF_UP))
        .activeCount(peopleCount)
        .build();
  }

  private BigDecimal toNavScale(BigDecimal nav) {
    return nav.setScale(4);
  }

  private Iterable<Fund> fundsBy(Optional<String> fundManagerName) {
    return fundManagerName
        .map(fundRepository::findAllByFundManagerNameIgnoreCase)
        .orElseGet(fundRepository::findAll);
  }
}
