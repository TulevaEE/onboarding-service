package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SecurityDataBuilder {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final int SCALE = 6;

  private final PositionPriceResolver positionPriceResolver;
  private final PublicHolidays publicHolidays;

  List<SecurityData> buildSecurityData(
      TulevaFund fund,
      List<ModelPortfolioAllocation> allocations,
      List<ModelPortfolioAllocation> previousAllocations,
      List<FundPosition> todayPositions,
      BigDecimal totalSecurities,
      LocalDate checkDate,
      LocalDate previousDate) {

    Map<String, FundPosition> todayByIsin =
        todayPositions.stream()
            .filter(p -> p.getAccountId() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, p -> p, (a, b) -> a));

    var todayCutoff = computePriceCutoff(fund, checkDate);
    var yesterdayCutoff = computePriceCutoff(fund, previousDate);

    var currentIsins =
        allocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var result =
        new ArrayList<>(
            allocations.stream()
                .filter(a -> a.getIsin() != null)
                .map(
                    a ->
                        buildOneSecurityData(
                            a.getIsin(),
                            a.getWeight(),
                            todayByIsin,
                            totalSecurities,
                            checkDate,
                            previousDate,
                            todayCutoff,
                            yesterdayCutoff))
                .toList());

    previousAllocations.stream()
        .filter(a -> a.getIsin() != null)
        .filter(a -> !currentIsins.contains(a.getIsin()))
        .filter(a -> todayByIsin.containsKey(a.getIsin()))
        .map(
            a ->
                buildOneSecurityData(
                    Objects.requireNonNull(
                        a.getIsin(), "Model portfolio allocation missing isin: fund=" + fund),
                    ZERO,
                    todayByIsin,
                    totalSecurities,
                    checkDate,
                    previousDate,
                    todayCutoff,
                    yesterdayCutoff))
        .forEach(result::add);

    return result;
  }

  List<SecurityData> blendTransitionWeights(
      List<SecurityData> securities,
      List<ModelPortfolioAllocation> allocations,
      List<ModelPortfolioAllocation> previousAllocations,
      List<FundPosition> positions,
      TulevaFund fund) {

    if (previousAllocations.isEmpty()) {
      return securities;
    }

    // Membership is decided by carrying weight, not by appearing in the table: a switch leaves the
    // exiting instrument in the model at 0% until it is liquidated, and read by ISIN presence
    // nothing would ever count as removed.
    var currentIsins = weightedIsins(allocations);
    var previousIsins = weightedIsins(previousAllocations);

    var addedIsins = new HashSet<>(currentIsins);
    addedIsins.removeAll(previousIsins);
    var removedIsins = new HashSet<>(previousIsins);
    removedIsins.removeAll(currentIsins);

    if (addedIsins.isEmpty() && removedIsins.isEmpty()) {
      return securities;
    }

    var positionIsins =
        positions.stream()
            .map(FundPosition::getAccountId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var knownIsins =
        allocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
    previousAllocations.stream()
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull)
        .forEach(knownIsins::add);
    var unexpectedIsins = new HashSet<>(positionIsins);
    unexpectedIsins.removeAll(knownIsins);

    if (!unexpectedIsins.isEmpty()) {
      log.warn(
          "Unexpected ISINs in portfolio, skipping transition blending: fund={}, unexpected={}",
          fund,
          unexpectedIsins);
      return securities;
    }

    var removedAndHeld = new HashSet<>(removedIsins);
    removedAndHeld.retainAll(positionIsins);

    if (removedAndHeld.isEmpty()) {
      return securities;
    }

    var transitionIsins = new HashSet<>(addedIsins);
    transitionIsins.addAll(removedIsins);

    log.info(
        "Transition blending applied: fund={}, removedAndHeld={}, addedIsins={}",
        fund,
        removedAndHeld,
        addedIsins);

    // The transition legs take their actual weights, so the legs that did not move have to give
    // up exactly that much - a model portfolio weighs 1, and one that weighs more scales the
    // benchmark return up with it and invents a tracking difference out of nothing.
    var transitionWeight =
        securities.stream()
            .filter(s -> transitionIsins.contains(s.isin()))
            .map(SecurityData::actualWeight)
            .reduce(ZERO, BigDecimal::add);
    var settledModelWeight =
        securities.stream()
            .filter(s -> !transitionIsins.contains(s.isin()))
            .map(SecurityData::modelWeight)
            .reduce(ZERO, BigDecimal::add);
    var remaining = BigDecimal.ONE.subtract(transitionWeight);
    if (remaining.signum() < 0 || settledModelWeight.signum() == 0) {
      log.warn(
          "Transition legs leave no room for the rest of the model, blending without rescaling: fund={}, transitionWeight={}, settledModelWeight={}",
          fund,
          transitionWeight,
          settledModelWeight);
      remaining = settledModelWeight;
    }
    var scale =
        settledModelWeight.signum() == 0
            ? BigDecimal.ONE
            : remaining.divide(settledModelWeight, 10, RoundingMode.HALF_UP);

    return securities.stream()
        .map(
            s ->
                transitionIsins.contains(s.isin())
                    ? new SecurityData(
                        s.isin(), s.actualWeight(), s.actualWeight(), s.today(), s.previous())
                    : new SecurityData(
                        s.isin(),
                        s.modelWeight().multiply(scale).setScale(SCALE, RoundingMode.HALF_UP),
                        s.actualWeight(),
                        s.today(),
                        s.previous()))
        .toList();
  }

  private static Set<String> weightedIsins(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .filter(a -> a.getWeight() != null && a.getWeight().signum() > 0)
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private SecurityData buildOneSecurityData(
      String isin,
      BigDecimal modelWeight,
      Map<String, FundPosition> todayByIsin,
      BigDecimal totalSecurities,
      LocalDate checkDate,
      LocalDate previousDate,
      Instant todayCutoff,
      Instant yesterdayCutoff) {

    var today = resolvePriceSnapshot(isin, checkDate, todayCutoff);
    var previous = resolvePriceSnapshot(isin, previousDate, yesterdayCutoff);

    var todayPos = todayByIsin.get(isin);
    var todayMarketValue = todayPos != null ? todayPos.getMarketValue() : null;
    var actualMarketValue = todayMarketValue != null ? todayMarketValue : ZERO;
    var actualWeight =
        totalSecurities.signum() != 0
            ? actualMarketValue.divide(totalSecurities, 6, RoundingMode.HALF_UP)
            : ZERO;

    return new SecurityData(isin, modelWeight, actualWeight, today, previous);
  }

  PriceSnapshot resolvePriceSnapshot(String isin, LocalDate date, Instant cutoff) {
    var resolved =
        positionPriceResolver
            .resolve(isin, date, cutoff)
            .filter(rp -> rp.validationStatus() == ValidationStatus.OK)
            .orElse(null);
    return new PriceSnapshot(
        resolved != null ? resolved.usedPrice() : null,
        resolved != null ? resolved.priceDate() : null);
  }

  @Nullable List<TrackingDifferenceCalculator.BodHolding> buildBodHoldings(
      TulevaFund fund,
      LocalDate checkDate,
      LocalDate previousDate,
      List<FundPosition> bodPositions,
      BigDecimal bodTotalSecurities) {
    var todayCutoff = computePriceCutoff(fund, checkDate);
    var anchorCutoff = computePriceCutoff(fund, previousDate);

    var holdings = new ArrayList<TrackingDifferenceCalculator.BodHolding>();
    var missingPrices = new ArrayList<String>();
    for (var pos : bodPositions) {
      var marketValue = pos.getMarketValue();
      if (marketValue == null || marketValue.signum() == 0) {
        continue;
      }
      var isin = pos.getAccountId();
      if (isin == null) {
        log.warn(
            "Begin-of-day securities position with value but no ISIN, skipping NAV residual gate: fund={}, checkDate={}, anchorDate={}, marketValue={}",
            fund,
            checkDate,
            previousDate,
            marketValue);
        return null;
      }
      var weight = marketValue.divide(bodTotalSecurities, SCALE, RoundingMode.HALF_UP);
      var today = resolvePriceSnapshot(isin, checkDate, todayCutoff);
      var previous = resolvePriceSnapshot(isin, previousDate, anchorCutoff);
      if (today.price() == null || previous.price() == null || previous.price().signum() == 0) {
        missingPrices.add(isin);
      }
      holdings.add(new TrackingDifferenceCalculator.BodHolding(isin, weight, today, previous));
    }

    if (!missingPrices.isEmpty()) {
      log.warn(
          "Missing begin-of-day prices for NAV residual, skipping gate: fund={}, checkDate={}, anchorDate={}, missingIsins={}",
          fund,
          checkDate,
          previousDate,
          missingPrices);
      return null;
    }
    return holdings;
  }

  static boolean isStaleDate(PriceSnapshot snapshot, LocalDate expectedDate) {
    return snapshot.date() != null && !snapshot.date().equals(expectedDate);
  }

  Instant computePriceCutoff(TulevaFund fund, LocalDate navDate) {
    var calculationDate = publicHolidays.nextWorkingDay(navDate);
    var cutoff = calculationDate.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();
    return cutoff.plus(Duration.ofMinutes(5));
  }
}
