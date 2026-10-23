package ee.tuleva.onboarding.fund;

import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.StreamSupport.stream;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.fund.statistics.PensionFundStatistics;
import ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsService;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
class FundService {

  private final FundRepository fundRepository;
  private final PensionFundStatisticsService pensionFundStatisticsService;
  private final FundNavValues fundNavValues;
  private final LocaleService localeService;
  private final SavingsFundUnitStats savingsFundUnitStats;
  private final SavingsFundNav savingsFundNav;
  private final FundInvestorsInRegister fundInvestorsInRegister;

  List<ExtendedApiFundResponse> getFunds(Optional<String> fundManagerName) {
    Map<String, Integer> holdersInRegister = holdersInRegister();
    return stream(fundsBy(fundManagerName).spliterator(), false)
        .sorted()
        .map(
            fund ->
                new ExtendedApiFundResponse(
                    fund, getStatistics(fund, holdersInRegister), localeService.getCurrentLocale()))
        .toList();
  }

  private Map<String, Integer> holdersInRegister() {
    try {
      return fundInvestorsInRegister.latestInvestorCounts();
    } catch (RuntimeException e) {
      log.error("Fund holders not read from the register, listing funds without them", e);
      return Map.of();
    }
  }

  private PensionFundStatistics getStatistics(Fund fund, Map<String, Integer> holdersInRegister) {
    List<PensionFundStatistics> statistics = pensionFundStatisticsService.getCachedStatistics();
    PensionFundStatistics statistic =
        statistics.stream()
            .filter(feedStatistic -> Objects.equals(feedStatistic.getIsin(), fund.getIsin()))
            .findFirst()
            .orElseGet(() -> fallbackNavStatistics(fund));
    return fund.isThirdPillar()
        ? withHolders(statistic, holdersInRegister.get(fund.getIsin()))
        : statistic;
  }

  private static PensionFundStatistics withHolders(
      PensionFundStatistics statistic, @Nullable Integer holders) {
    return holders == null ? statistic : statistic.withActiveCount(holders);
  }

  private PensionFundStatistics fallbackNavStatistics(Fund fund) {
    boolean isSavingsFund = savingsFundNav.isSavingsFund(fund.getIsin());
    Optional<FundNavValues.NavPoint> latestValue =
        isSavingsFund
            ? fundNavValues.latestValueOnOrBefore(fund.getIsin(), savingsFundNav.safeMaxNavDate())
            : fundNavValues.lastValue(fund.getIsin());
    return latestValue
        .map(fundValue -> buildSavingsFundStatistics(fund, fundValue))
        .orElseGet(PensionFundStatistics::getNull);
  }

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private PensionFundStatistics buildSavingsFundStatistics(
      Fund fund, FundNavValues.NavPoint latestFundValue) {
    if (!savingsFundNav.isSavingsFund(fund.getIsin())) {
      return PensionFundStatistics.builder().nav(latestFundValue.value()).build();
    }

    var currentBalance = savingsFundUnitStats.unitsOutstanding();
    var cutoff = latestFundValue.date().plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    var balanceAtCutoff = savingsFundUnitStats.unitsOutstandingAt(cutoff);
    var peopleCount = savingsFundUnitStats.unitHolderCount();

    boolean issuanceCompleted = currentBalance.compareTo(balanceAtCutoff) != 0;

    if (issuanceCompleted) {
      var nav = toNavScale(fund.getIsin(), latestFundValue.value());
      return PensionFundStatistics.builder()
          .nav(nav)
          .volume(currentBalance.multiply(nav).setScale(2, HALF_UP))
          .activeCount(peopleCount)
          .build();
    }

    var previousNav =
        toNavScale(
            fund.getIsin(),
            fundNavValues
                .latestValueOnOrBefore(fund.getIsin(), latestFundValue.date().minusDays(1))
                .map(FundNavValues.NavPoint::value)
                .orElse(latestFundValue.value()));
    return PensionFundStatistics.builder()
        .nav(previousNav)
        .volume(currentBalance.multiply(previousNav).setScale(2, HALF_UP))
        .activeCount(peopleCount)
        .build();
  }

  private BigDecimal toNavScale(String isin, BigDecimal nav) {
    return TulevaFund.findByIsin(isin)
        .map(tulevaFund -> nav.setScale(tulevaFund.getNavScale(), HALF_UP))
        .orElse(nav);
  }

  List<NavValueResponse> getNavHistory(String isin, LocalDate startDate, LocalDate endDate) {
    Fund fund = fundRepository.findByIsin(isin);
    if (fund == null) {
      throw new ResponseStatusException(NOT_FOUND);
    }
    return navHistoryFor(fund, startDate, endDate);
  }

  List<FundNavHistoryResponse> getNavHistories(
      Integer pillar, LocalDate startDate, LocalDate endDate) {
    return fundRepository.findAllByPillarAndStatus(pillar, Fund.FundStatus.ACTIVE).stream()
        .map(
            fund ->
                new FundNavHistoryResponse(fund.getIsin(), navHistoryFor(fund, startDate, endDate)))
        .toList();
  }

  private List<NavValueResponse> navHistoryFor(Fund fund, LocalDate startDate, LocalDate endDate) {
    LocalDate start = startDate != null ? startDate : LocalDate.EPOCH;
    LocalDate end = endDate != null ? endDate : LocalDate.of(9999, 12, 31);
    if (savingsFundNav.isSavingsFund(fund.getIsin())) {
      LocalDate safeMaxDate = savingsFundNav.safeMaxNavDate();
      if (end.isAfter(safeMaxDate)) {
        end = safeMaxDate;
      }
    }
    if (start.isAfter(end)) {
      return List.of();
    }
    return fundNavValues.valuesBetween(fund.getIsin(), start, end).stream()
        .map(
            navPoint ->
                new NavValueResponse(navPoint.date(), toNavScale(fund.getIsin(), navPoint.value())))
        .toList();
  }

  byte[] getNavHistoryCsv(String isin, LocalDate startDate, LocalDate endDate) {
    return new NavHistoryCsv(isin, getNavHistory(isin, startDate, endDate)).toBytes();
  }

  private Iterable<Fund> fundsBy(Optional<String> fundManagerName) {
    return fundManagerName
        .map(fundRepository::findAllByFundManagerNameIgnoreCase)
        .orElseGet(fundRepository::findAll);
  }
}
