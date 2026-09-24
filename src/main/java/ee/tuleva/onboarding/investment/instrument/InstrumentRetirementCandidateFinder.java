package ee.tuleva.onboarding.investment.instrument;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InstrumentRetirementCandidateFinder {

  private static final int DEFAULT_NAV_DATES_OFF_THE_BOOKS_BEFORE_RETIRING = 5;

  private final InstrumentReferenceService instrumentReferenceService;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final FundPositionRepository fundPositionRepository;
  private final BenchmarkInstruments benchmarkInstruments;
  private final InstrumentRetirementThreshold retirementThreshold;
  private final Clock clock;

  record RetirementCandidate(
      String isin, String displayName, LocalDate offTheBooksSince, long navDatesOffTheBooks) {}

  List<RetirementCandidate> findCandidates() {
    var today = LocalDate.now(clock);
    var isinsInLiveOrUpcomingModels = liveAndUpcomingModelIsins(today);
    var heldIsins = heldIsins();
    var pricedForBenchmarks = pricedForBenchmarks();
    var requiredNavDates = requiredNavDatesOffTheBooks(today);

    return instrumentReferenceService.activeInstruments().stream()
        .filter(instrument -> !isinsInLiveOrUpcomingModels.contains(instrument.getIsin()))
        .filter(instrument -> !heldIsins.contains(instrument.getIsin()))
        .filter(instrument -> !pricedForBenchmarks.contains(instrument.getIsin()))
        .map(this::toCandidateUnlessAwaitingItsFirstModel)
        .flatMap(Optional::stream)
        .filter(candidate -> candidate.navDatesOffTheBooks() >= requiredNavDates)
        .toList();
  }

  private long requiredNavDatesOffTheBooks(LocalDate asOf) {
    return retirementThreshold
        .requiredNavDatesOffTheBooks(asOf)
        .map(configured -> configured.setScale(0, RoundingMode.CEILING).longValue())
        .filter(navDates -> navDates > 0)
        .orElse((long) DEFAULT_NAV_DATES_OFF_THE_BOOKS_BEFORE_RETIRING);
  }

  private Set<String> pricedForBenchmarks() {
    return Stream.concat(
            instrumentReferenceService.benchmarkProxyIsins().stream(),
            benchmarkInstruments.benchmarkIsins().stream())
        .collect(toSet());
  }

  private Optional<RetirementCandidate> toCandidateUnlessAwaitingItsFirstModel(
      InstrumentReference instrument) {
    return allocationRepository
        .findLatestEffectiveDateByIsin(instrument.getIsin())
        .map(lastInModelOn -> toCandidate(instrument, lastInModelOn));
  }

  private RetirementCandidate toCandidate(InstrumentReference instrument, LocalDate lastInModelOn) {
    var offTheBooksSince = offTheBooksSince(instrument.getIsin(), lastInModelOn);
    return new RetirementCandidate(
        instrument.getIsin(),
        instrument.getDisplayName(),
        offTheBooksSince,
        navDatesOffTheBooks(instrument.getIsin(), offTheBooksSince));
  }

  private long navDatesOffTheBooks(String isin, LocalDate offTheBooksSince) {
    var fundsThatHeldIt = fundPositionRepository.findFundsThatHeld(isin, SECURITY);
    if (fundsThatHeldIt.isEmpty()) {
      return navDatesAnyFundHasReportedSince(offTheBooksSince);
    }
    return fundsThatHeldIt.stream()
        .mapToLong(fund -> navDatesReportedSince(fund, offTheBooksSince))
        .min()
        .orElse(0);
  }

  private long navDatesAnyFundHasReportedSince(LocalDate offTheBooksSince) {
    return navCalculatingFunds()
        .mapToLong(fund -> navDatesReportedSince(fund, offTheBooksSince))
        .max()
        .orElse(0);
  }

  private long navDatesReportedSince(TulevaFund fund, LocalDate offTheBooksSince) {
    return fundPositionRepository.countNavDatesReportingAfter(fund, SECURITY, offTheBooksSince);
  }

  private LocalDate offTheBooksSince(String isin, LocalDate lastInModelOn) {
    return fundPositionRepository
        .findLatestNavDateHeld(isin, SECURITY)
        .filter(lastHeldOn -> lastHeldOn.isAfter(lastInModelOn))
        .orElse(lastInModelOn);
  }

  private Set<String> liveAndUpcomingModelIsins(LocalDate today) {
    return navCalculatingFunds()
        .flatMap(fund -> liveAndUpcomingAllocations(fund, today))
        .flatMap(allocation -> Stream.ofNullable(allocation.getIsin()))
        .collect(toSet());
  }

  private Stream<ModelPortfolioAllocation> liveAndUpcomingAllocations(
      TulevaFund fund, LocalDate today) {
    var upcoming =
        allocationRepository.findFutureEffectiveDates(fund, today).stream()
            .flatMap(date -> allocationRepository.findByFundAndEffectiveDate(fund, date).stream());
    return Stream.concat(allocationRepository.findLatestByFundAsOf(fund, today).stream(), upcoming);
  }

  private Set<String> heldIsins() {
    return navCalculatingFunds().flatMap(this::heldIsins).collect(toSet());
  }

  private Stream<String> heldIsins(TulevaFund fund) {
    return fundPositionRepository.findLatestNavDateReporting(fund, SECURITY).stream()
        .flatMap(
            navDate ->
                fundPositionRepository
                    .findByNavDateAndFundAndAccountType(navDate, fund, SECURITY)
                    .stream())
        .filter(InstrumentRetirementCandidateFinder::isNotFullyDeinvested)
        .flatMap(position -> Stream.ofNullable(position.getAccountId()));
  }

  private static boolean isNotFullyDeinvested(FundPosition position) {
    var quantity = position.getQuantity();
    return quantity != null && quantity.compareTo(ZERO) != 0;
  }

  private static Stream<TulevaFund> navCalculatingFunds() {
    return Arrays.stream(TulevaFund.values()).filter(TulevaFund::hasNavCalculation);
  }
}
