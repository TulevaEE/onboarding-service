package ee.tuleva.onboarding.account.portfolio;

import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SECOND_PILLAR;
import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.THIRD_PILLAR;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.account.SavingsFundIsin;
import ee.tuleva.onboarding.account.SavingsFundNav;
import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.account.transaction.TransactionService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.ReturnsService;
import ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioService {

  private static final Map<PortfolioGroup, String> RETURN_KEYS =
      Map.of(
          SECOND_PILLAR,
          PersonalReturnProvider.SECOND_PILLAR,
          THIRD_PILLAR,
          PersonalReturnProvider.THIRD_PILLAR);

  private final TransactionService transactionService;
  private final FundRepository fundRepository;
  private final FundValueQueries fundValueQueries;
  private final SavingsFundIsin savingsFundConfiguration;
  private final ReturnsService returnsService;
  private final SavingsFundNav fundNavProvider;
  private final Clock clock;

  public Portfolio getPortfolio(
      AuthenticatedPerson person, @Nullable LocalDate from, LocalDate to) {
    List<Transaction> transactions = transactionService.getTransactions(person);
    LocalDate startDate = from == null ? firstHoldingDate(transactions, to) : from;
    Map<String, PortfolioGroup> groupByIsin = groupByIsin();
    Set<String> heldIsins = PortfolioValuation.heldIsins(transactions, groupByIsin);

    PortfolioValuation valuation =
        new PortfolioValuation(transactions, groupByIsin, navHistory(heldIsins, startDate, to));
    List<PortfolioGroup> groups = valuation.heldGroups();
    Map<PortfolioGroup, BigDecimal> rates = annualReturnRates(person, groups, startDate, to);

    return Portfolio.builder()
        .from(startDate)
        .to(to)
        .groups(summaries(valuation, groups, rates, startDate, to))
        .series(valuation.series(startDate, to))
        .build();
  }

  private static LocalDate firstHoldingDate(List<Transaction> transactions, LocalDate to) {
    return transactions.stream()
        .map(PortfolioValuation::pricingDayOf)
        .min(naturalOrder())
        .orElse(to);
  }

  private static List<Portfolio.GroupSummary> summaries(
      PortfolioValuation valuation,
      List<PortfolioGroup> groups,
      Map<PortfolioGroup, BigDecimal> rates,
      LocalDate from,
      LocalDate to) {
    return groups.stream()
        .map(
            group ->
                valuation.summaryOf(group, from, to).toBuilder()
                    .annualReturnRate(rates.get(group))
                    .build())
        .toList();
  }

  private Map<String, PortfolioGroup> groupByIsin() {
    Map<String, PortfolioGroup> groups = new HashMap<>();
    fundRepository
        .findAll()
        .forEach(fund -> groupOf(fund).ifPresent(group -> groups.put(fund.getIsin(), group)));
    groups.put(savingsFundConfiguration.getIsin(), PortfolioGroup.SAVINGS_FUND);
    return Map.copyOf(groups);
  }

  private Optional<PortfolioGroup> groupOf(Fund fund) {
    if (savingsFundConfiguration.getIsin().equals(fund.getIsin())) {
      return Optional.of(PortfolioGroup.SAVINGS_FUND);
    }
    if (fund.getPillar() == null) {
      return Optional.empty();
    }
    return switch (fund.getPillar()) {
      case 2 -> Optional.of(SECOND_PILLAR);
      case 3 -> Optional.of(THIRD_PILLAR);
      default -> Optional.empty();
    };
  }

  private Map<String, Map<LocalDate, BigDecimal>> navHistory(
      Set<String> isins, LocalDate from, LocalDate to) {
    Map<String, Map<LocalDate, BigDecimal>> history = new HashMap<>();

    isins.forEach(
        isin -> {
          LocalDate end = latestVisibleDate(isin, to);
          Map<LocalDate, BigDecimal> prices = new HashMap<>();
          fundValueQueries
              .getLatestValue(isin, earlierOf(from.minusDays(1), end))
              .ifPresent(value -> prices.put(value.date(), value.value()));
          fundValueQueries
              .findValuesBetweenDates(isin, from, end)
              .forEach(value -> prices.put(value.date(), value.value()));
          history.put(isin, prices);
        });

    return history;
  }

  private LocalDate latestVisibleDate(String isin, LocalDate to) {
    if (!savingsFundConfiguration.getIsin().equals(isin)) {
      return to;
    }
    return earlierOf(to, fundNavProvider.safeMaxNavDate());
  }

  private static LocalDate earlierOf(LocalDate date, LocalDate other) {
    return date.isBefore(other) ? date : other;
  }

  private Map<PortfolioGroup, BigDecimal> annualReturnRates(
      AuthenticatedPerson person, List<PortfolioGroup> groups, LocalDate from, LocalDate to) {
    return RETURN_KEYS.entrySet().stream()
        .filter(entry -> groups.contains(entry.getKey()))
        .flatMap(
            entry ->
                annualReturnRate(person, entry.getValue(), from, to).stream()
                    .map(rate -> Map.entry(entry.getKey(), rate)))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private boolean hasMeasurableLength(LocalDate from, LocalDate to) {
    return from.isBefore(earlierOf(to, LocalDate.now(clock)));
  }

  private Optional<BigDecimal> annualReturnRate(
      AuthenticatedPerson person, String key, LocalDate from, LocalDate to) {
    if (!hasMeasurableLength(from, to)) {
      return Optional.empty();
    }
    return returnsService.get(person, from, to, List.of(key)).getReturns().stream()
        .map(Returns.Return::getRate)
        .filter(Objects::nonNull)
        .findFirst();
  }
}
