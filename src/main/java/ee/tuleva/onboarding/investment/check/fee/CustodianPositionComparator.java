package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CustodianPositionComparator {

  private static final List<AccountType> CUSTODIAN_TYPES = List.of(CASH, RECEIVABLES, LIABILITY);
  private static final BigDecimal BASIS_POINTS = new BigDecimal("10000");
  private static final int BASIS_POINT_SCALE = 2;
  private static final int CENTS = 2;

  private final FundPositionRepository fundPositionRepository;
  private final FundNavQueryService fundNavQueryService;
  private final BigDecimal tolerance;

  CustodianPositionComparator(
      FundPositionRepository fundPositionRepository,
      FundNavQueryService fundNavQueryService,
      @Value("${investment.fee-check.custodian-tolerance:1.00}") BigDecimal tolerance) {
    this.fundPositionRepository = fundPositionRepository;
    this.fundNavQueryService = fundNavQueryService;
    this.tolerance = tolerance;
  }

  Optional<CustodianDayComparison> compare(TulevaFund fund, LocalDate navDate) {
    return fundNavQueryService
        .findLatestCalculation(fund.getCode(), navDate)
        .map(calculation -> compare(fund, navDate, calculation));
  }

  private CustodianDayComparison compare(
      TulevaFund fund, LocalDate navDate, NavCalculation calculation) {
    var positions = fundPositionRepository.findCustodianSourced(fund, navDate, fund.getIsin());
    var reported = reportedValues(positions);
    var recognised = recognisedValues(calculation);

    var navImpact =
        totalDifference(reported, recognised)
            .add(securityDifference(positions, calculation))
            .setScale(CENTS, HALF_UP);

    return new CustodianDayComparison(
        navDate,
        differingLines(reported, recognised),
        navPredatesReport(fund, navDate, calculation),
        navImpact,
        basisPointsOf(navImpact, calculation.assetsUnderManagement()));
  }

  private List<CustodianLineDifference> differingLines(
      Map<String, BigDecimal> reported, Map<String, BigDecimal> recognised) {
    return unionOfKeys(reported, recognised).stream()
        .map(
            name ->
                new CustodianLineDifference(
                    name, reported.getOrDefault(name, ZERO), recognised.getOrDefault(name, ZERO)))
        .filter(difference -> difference.difference().abs().compareTo(tolerance) > 0)
        .toList();
  }

  private BigDecimal totalDifference(
      Map<String, BigDecimal> reported, Map<String, BigDecimal> recognised) {
    return unionOfKeys(reported, recognised).stream()
        .map(
            name -> reported.getOrDefault(name, ZERO).subtract(recognised.getOrDefault(name, ZERO)))
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal securityDifference(List<FundPosition> positions, NavCalculation calculation) {
    var reported = reportedQuantities(positions);
    var recognised = recognisedQuantities(calculation);
    var prices = pricesByIsinPreferringOurNav(positions, calculation);

    return unionOfKeys(reported, recognised).stream()
        .map(
            isin ->
                reported
                    .getOrDefault(isin, ZERO)
                    .subtract(recognised.getOrDefault(isin, ZERO))
                    .multiply(prices.getOrDefault(isin, ZERO)))
        .reduce(ZERO, BigDecimal::add);
  }

  private Map<String, BigDecimal> reportedValues(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> CUSTODIAN_TYPES.contains(position.getAccountType()))
        .collect(
            toMap(
                FundPosition::getAccountName,
                position -> value(position.getMarketValue()),
                BigDecimal::add,
                LinkedHashMap::new));
  }

  private Map<String, BigDecimal> recognisedValues(NavCalculation calculation) {
    return calculation.custodianComparableLines().stream()
        .collect(
            toMap(
                NavAccountLine::accountName,
                NavAccountLine::value,
                BigDecimal::add,
                LinkedHashMap::new));
  }

  private Map<String, BigDecimal> reportedQuantities(List<FundPosition> positions) {
    return securities(positions)
        .collect(
            toMap(
                FundPosition::getAccountId,
                position -> value(position.getQuantity()),
                BigDecimal::add,
                LinkedHashMap::new));
  }

  private Map<String, BigDecimal> recognisedQuantities(NavCalculation calculation) {
    var quantities = new LinkedHashMap<String, BigDecimal>();
    for (var line : calculation.securityLines()) {
      var isin = line.accountId();
      if (isin != null) {
        quantities.merge(isin, line.units(), BigDecimal::add);
      }
    }
    return quantities;
  }

  private Map<String, BigDecimal> pricesByIsinPreferringOurNav(
      List<FundPosition> positions, NavCalculation calculation) {
    var prices = new LinkedHashMap<String, BigDecimal>();
    securities(positions)
        .filter(position -> position.getMarketPrice() != null)
        .forEach(position -> prices.put(position.getAccountId(), position.getMarketPrice()));
    for (var line : calculation.securityLines()) {
      var isin = line.accountId();
      if (isin != null && line.marketPrice() != null) {
        prices.put(isin, line.marketPrice());
      }
    }
    return prices;
  }

  private Stream<FundPosition> securities(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountType() == SECURITY)
        .filter(position -> position.getAccountId() != null);
  }

  private boolean navPredatesReport(
      TulevaFund fund, LocalDate navDate, NavCalculation calculation) {
    return fundPositionRepository
        .findLastWrittenAt(fund, navDate)
        .filter(writtenAt -> writtenAt.isAfter(calculation.calculatedAt()))
        .isPresent();
  }

  private BigDecimal basisPointsOf(BigDecimal impact, BigDecimal fundValue) {
    if (fundValue.signum() == 0) {
      return ZERO;
    }
    return impact.multiply(BASIS_POINTS).divide(fundValue, BASIS_POINT_SCALE, HALF_UP);
  }

  private Set<String> unionOfKeys(Map<String, BigDecimal> first, Map<String, BigDecimal> second) {
    var names = new TreeSet<>(first.keySet());
    names.addAll(second.keySet());
    return names;
  }

  private BigDecimal value(@Nullable BigDecimal amount) {
    return amount == null ? ZERO : amount;
  }
}
