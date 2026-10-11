package ee.tuleva.onboarding.investment.instrument;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
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

  private static final int NAV_DATES_NEITHER_HELD_NOR_MODELLED_BEFORE_RETIRING = 5;

  private final InstrumentReferenceService instrumentReferenceService;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final FundPositionRepository fundPositionRepository;
  private final BenchmarkInstruments benchmarkInstruments;
  private final Clock clock;

  record RetirementCandidate(
      String isin,
      String displayName,
      LocalDate retirementClockStartedOn,
      long navDatesSinceClockStarted) {

    String describe() {
      return "%s %s — neither held nor in a model after %s, %d NAV dates since"
          .formatted(isin, displayName, retirementClockStartedOn, navDatesSinceClockStarted);
    }
  }

  List<RetirementCandidate> findCandidates() {
    var today = LocalDate.now(clock);
    var isinsInLiveOrUpcomingModels = liveAndUpcomingModelIsins(today);
    var heldIsins = heldIsins();
    var pricedForBenchmarks = pricedForBenchmarks();

    return instrumentReferenceService.activeInstruments().stream()
        .filter(instrument -> !isinsInLiveOrUpcomingModels.contains(instrument.getIsin()))
        .filter(instrument -> !heldIsins.contains(instrument.getIsin()))
        .filter(instrument -> !pricedForBenchmarks.contains(instrument.getIsin()))
        .map(this::toCandidateUnlessAwaitingItsFirstModel)
        .flatMap(Optional::stream)
        .filter(
            candidate ->
                candidate.navDatesSinceClockStarted()
                    >= NAV_DATES_NEITHER_HELD_NOR_MODELLED_BEFORE_RETIRING)
        .toList();
  }

  private Set<String> pricedForBenchmarks() {
    return Stream.concat(
            instrumentReferenceService.benchmarkProxyIsins().stream(),
            benchmarkInstruments.benchmarkIsins().stream())
        .collect(toSet());
  }

  private Optional<RetirementCandidate> toCandidateUnlessAwaitingItsFirstModel(
      InstrumentReference instrument) {
    var fundsWhoseModelNamedIt =
        allocationRepository.findFundsWhoseModelNamed(instrument.getIsin()).stream()
            .filter(TulevaFund::hasNavCalculation)
            .toList();
    if (fundsWhoseModelNamedIt.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toCandidate(instrument, fundsWhoseModelNamedIt));
  }

  private RetirementCandidate toCandidate(
      InstrumentReference instrument, List<TulevaFund> fundsWhoseModelNamedIt) {
    var isin = instrument.getIsin();
    var clockStartedOn = retirementClockStartedOn(isin, fundsWhoseModelNamedIt);
    return new RetirementCandidate(
        isin,
        instrument.getDisplayName(),
        clockStartedOn,
        navDatesSinceClockStarted(isin, clockStartedOn));
  }

  private LocalDate retirementClockStartedOn(String isin, List<TulevaFund> fundsWhoseModelNamedIt) {
    var droppedFromTheLastModelOn =
        fundsWhoseModelNamedIt.stream()
            .map(fund -> droppedFromModelOn(fund, isin))
            .max(naturalOrder())
            .orElseThrow();
    return fundPositionRepository
        .findLatestNavDateHeld(isin, SECURITY)
        .filter(lastHeldOn -> lastHeldOn.isAfter(droppedFromTheLastModelOn))
        .orElse(droppedFromTheLastModelOn);
  }

  private LocalDate droppedFromModelOn(TulevaFund fund, String isin) {
    return allocationRepository
        .findEffectiveDateOfFirstVersionWithout(fund, isin)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Latest model portfolio version still names the instrument: fund=%s, isin=%s"
                        .formatted(fund, isin)));
  }

  private long navDatesSinceClockStarted(String isin, LocalDate clockStartedOn) {
    var fundsThatHeldIt = fundPositionRepository.findFundsThatHeld(isin, SECURITY);
    if (fundsThatHeldIt.isEmpty()) {
      return navDatesAnyFundHasReportedSince(clockStartedOn);
    }
    return fundsThatHeldIt.stream()
        .mapToLong(fund -> navDatesReportedSince(fund, clockStartedOn))
        .min()
        .orElseThrow();
  }

  private long navDatesAnyFundHasReportedSince(LocalDate clockStartedOn) {
    return navCalculatingFunds()
        .mapToLong(fund -> navDatesReportedSince(fund, clockStartedOn))
        .max()
        .orElseThrow();
  }

  private long navDatesReportedSince(TulevaFund fund, LocalDate clockStartedOn) {
    return fundPositionRepository.countNavDatesReportingAfter(fund, SECURITY, clockStartedOn);
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
        .filter(position -> !isSoldOut(position))
        .flatMap(position -> Stream.ofNullable(position.getAccountId()));
  }

  private static boolean isSoldOut(FundPosition position) {
    var quantity = position.getQuantity();
    return quantity != null && quantity.compareTo(ZERO) == 0;
  }

  private static Stream<TulevaFund> navCalculatingFunds() {
    return Arrays.stream(TulevaFund.values()).filter(TulevaFund::hasNavCalculation);
  }
}
