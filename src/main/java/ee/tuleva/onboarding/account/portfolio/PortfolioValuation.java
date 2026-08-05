package ee.tuleva.onboarding.account.portfolio;

import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import ee.tuleva.onboarding.account.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public class PortfolioValuation {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final int MONEY_SCALE = 2;
  private static final int PERCENTAGE_SCALE = 2;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final List<Transaction> transactions;
  private final Map<String, PortfolioGroup> groupByIsin;
  private final Map<PortfolioGroup, Set<String>> isinsByGroup;
  private final Map<String, NavigableMap<LocalDate, BigDecimal>> pricesByIsin = new HashMap<>();
  private final Map<String, NavigableMap<LocalDate, BigDecimal>> unitsHeldByIsin;
  private final Set<String> unvaluableIsins;

  public PortfolioValuation(
      List<Transaction> transactions,
      Map<String, PortfolioGroup> groupByIsin,
      Map<String, ? extends Map<LocalDate, BigDecimal>> navHistoryByIsin) {
    this.transactions = transactions;
    this.groupByIsin = groupByIsin;
    this.isinsByGroup = isinsByGroup(groupByIsin);
    navHistoryByIsin.forEach((isin, navs) -> pricesByIsin.put(isin, new TreeMap<>(navs)));
    this.unitsHeldByIsin = runningUnitCounts(transactions);
    this.unvaluableIsins = isinsWithUnknownUnits(transactions);
  }

  private static Set<String> isinsWithUnknownUnits(List<Transaction> transactions) {
    return transactions.stream()
        .filter(transaction -> transaction.units() == null)
        .map(Transaction::isin)
        .collect(toCollection(TreeSet::new));
  }

  private static Map<PortfolioGroup, Set<String>> isinsByGroup(
      Map<String, PortfolioGroup> groupByIsin) {
    return groupByIsin.entrySet().stream()
        .collect(
            groupingBy(
                Map.Entry::getValue, mapping(Map.Entry::getKey, toCollection(TreeSet::new))));
  }

  private static Map<String, NavigableMap<LocalDate, BigDecimal>> runningUnitCounts(
      List<Transaction> transactions) {
    Map<String, NavigableMap<LocalDate, BigDecimal>> byIsin = new HashMap<>();

    transactions.stream()
        .filter(transaction -> transaction.units() != null)
        .sorted(comparing(Transaction::priceTime))
        .forEach(
            transaction -> {
              NavigableMap<LocalDate, BigDecimal> running =
                  byIsin.computeIfAbsent(transaction.isin(), isin -> new TreeMap<>());
              BigDecimal carried =
                  running.isEmpty() ? BigDecimal.ZERO : running.lastEntry().getValue();
              running.put(pricingDayOf(transaction), carried.add(signedUnits(transaction)));
            });

    return byIsin;
  }

  private static LocalDate pricingDayOf(Transaction transaction) {
    return transaction.priceTime().atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private static BigDecimal signedUnits(Transaction transaction) {
    BigDecimal units = transaction.units().abs();
    return transaction.isAcquisition() ? units : units.negate();
  }

  public @Nullable BigDecimal navAt(String isin, LocalDate date) {
    NavigableMap<LocalDate, BigDecimal> prices = pricesByIsin.get(isin);
    return prices == null ? null : valueOrNull(prices.floorEntry(date));
  }

  private static @Nullable BigDecimal valueOrNull(
      Map.@Nullable Entry<LocalDate, BigDecimal> entry) {
    return entry == null ? null : entry.getValue();
  }

  public BigDecimal unitsAt(String isin, LocalDate date) {
    NavigableMap<LocalDate, BigDecimal> held = unitsHeldByIsin.get(isin);
    if (held == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal units = valueOrNull(held.floorEntry(date));
    return units == null ? BigDecimal.ZERO : units;
  }

  public @Nullable BigDecimal valueAt(Set<String> isins, LocalDate date) {
    if (isins.stream().anyMatch(unvaluableIsins::contains)) {
      return null;
    }

    BigDecimal total = BigDecimal.ZERO;

    for (String isin : isins) {
      BigDecimal units = unitsAt(isin, date);
      if (units.signum() == 0) {
        continue;
      }
      BigDecimal nav = navAt(isin, date);
      if (nav == null) {
        return null;
      }
      total = total.add(units.multiply(nav));
    }

    return total.setScale(MONEY_SCALE, HALF_UP);
  }

  public List<LocalDate> pricingDates(LocalDate from, LocalDate to) {
    return pricesByIsin.values().stream()
        .flatMap(prices -> prices.subMap(from, true, to, true).keySet().stream())
        .collect(toCollection(TreeSet::new))
        .stream()
        .toList();
  }

  public List<Portfolio.ValuePoint> series(LocalDate from, LocalDate to) {
    List<PortfolioGroup> groups = heldGroups();
    return pricingDates(from, to).stream()
        .map(date -> new Portfolio.ValuePoint(date, valuesByGroupAt(groups, date)))
        .toList();
  }

  private Map<PortfolioGroup, @Nullable BigDecimal> valuesByGroupAt(
      List<PortfolioGroup> groups, LocalDate date) {
    Map<PortfolioGroup, @Nullable BigDecimal> values = new LinkedHashMap<>();
    groups.forEach(group -> values.put(group, valueAt(isinsOf(group), date)));
    return values;
  }

  public Portfolio.GroupSummary summaryOf(PortfolioGroup group, LocalDate from, LocalDate to) {
    Set<String> isins = isinsOf(group);
    BigDecimal startValue = valueAt(isins, from.minusDays(1));
    BigDecimal endValue = valueAt(isins, to);
    BigDecimal contributions = cashFlowBetween(isins, from, to, true);
    BigDecimal withdrawals = cashFlowBetween(isins, from, to, false);

    Portfolio.GroupSummary.GroupSummaryBuilder summary =
        Portfolio.GroupSummary.builder()
            .group(group)
            .startValue(startValue)
            .endValue(endValue)
            .contributions(contributions)
            .withdrawals(withdrawals);

    if (startValue == null || endValue == null) {
      return summary.build();
    }

    BigDecimal gain = endValue.add(withdrawals).subtract(startValue).subtract(contributions);
    return summary
        .gain(gain)
        .gainPercentage(percentageOf(gain, startValue.add(contributions)))
        .build();
  }

  private static BigDecimal percentageOf(BigDecimal gain, BigDecimal invested) {
    return invested.signum() > 0
        ? gain.multiply(HUNDRED).divide(invested, PERCENTAGE_SCALE, HALF_UP)
        : BigDecimal.ZERO.setScale(PERCENTAGE_SCALE);
  }

  public List<PortfolioGroup> heldGroups() {
    Set<String> held = heldIsins(transactions, groupByIsin);
    return Arrays.stream(PortfolioGroup.values())
        .filter(group -> held.stream().anyMatch(isin -> group == groupByIsin.get(isin)))
        .toList();
  }

  public static Set<String> heldIsins(
      List<Transaction> transactions, Map<String, PortfolioGroup> groupByIsin) {
    return transactions.stream()
        .map(Transaction::isin)
        .filter(groupByIsin::containsKey)
        .collect(toCollection(TreeSet::new));
  }

  private Set<String> isinsOf(PortfolioGroup group) {
    return isinsByGroup.getOrDefault(group, Set.of());
  }

  private BigDecimal cashFlowBetween(
      Set<String> isins, LocalDate from, LocalDate to, boolean acquisitions) {
    return transactions.stream()
        .filter(transaction -> isins.contains(transaction.isin()))
        .filter(transaction -> !pricingDayOf(transaction).isBefore(from))
        .filter(transaction -> !pricingDayOf(transaction).isAfter(to))
        .filter(transaction -> transaction.isAcquisition() == acquisitions)
        .map(transaction -> transaction.amount().abs())
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(MONEY_SCALE, HALF_UP);
  }
}
