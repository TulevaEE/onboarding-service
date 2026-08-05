package ee.tuleva.onboarding.account.portfolio;

import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SECOND_PILLAR;
import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.THIRD_PILLAR;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.account.transaction.TransactionService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.returns.Returns;
import ee.tuleva.onboarding.comparisons.returns.ReturnsService;
import ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
  private final FundValueRepository fundValueRepository;
  private final SavingsFundConfiguration savingsFundConfiguration;
  private final ReturnsService returnsService;

  public Portfolio getPortfolio(AuthenticatedPerson person, LocalDate from, LocalDate to) {
    List<Transaction> transactions = transactionService.getTransactions(person);
    Map<String, PortfolioGroup> groupByIsin = groupByIsin();
    Set<String> heldIsins = heldIsins(transactions, groupByIsin);

    PortfolioValuation valuation =
        new PortfolioValuation(transactions, groupByIsin, navHistory(heldIsins, from, to));
    List<PortfolioGroup> groups = valuation.heldGroups();
    Map<PortfolioGroup, BigDecimal> rates = annualReturnRates(person, groups, from, to);

    return Portfolio.builder()
        .from(from)
        .to(to)
        .groups(summaries(valuation, groups, rates, from, to))
        .series(valuation.series(from, to))
        .build();
  }

  private static List<Portfolio.GroupSummary> summaries(
      PortfolioValuation valuation,
      List<PortfolioGroup> groups,
      Map<PortfolioGroup, BigDecimal> rates,
      LocalDate from,
      LocalDate to) {
    return groups.stream()
        .map(group -> withAnnualReturn(valuation.summaryOf(group, from, to), rates.get(group)))
        .toList();
  }

  private static Portfolio.GroupSummary withAnnualReturn(
      Portfolio.GroupSummary summary, @Nullable BigDecimal rate) {
    return Portfolio.GroupSummary.builder()
        .group(summary.group())
        .startValue(summary.startValue())
        .endValue(summary.endValue())
        .contributions(summary.contributions())
        .withdrawals(summary.withdrawals())
        .gain(summary.gain())
        .gainPercentage(summary.gainPercentage())
        .annualReturnRate(rate)
        .build();
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

  private static Set<String> heldIsins(
      List<Transaction> transactions, Map<String, PortfolioGroup> groupByIsin) {
    return transactions.stream()
        .map(Transaction::isin)
        .filter(groupByIsin::containsKey)
        .collect(toCollection(TreeSet::new));
  }

  private Map<String, Map<LocalDate, BigDecimal>> navHistory(
      Set<String> isins, LocalDate from, LocalDate to) {
    Map<String, Map<LocalDate, BigDecimal>> history = new HashMap<>();

    isins.forEach(
        isin -> {
          Map<LocalDate, BigDecimal> prices = new HashMap<>();
          fundValueRepository
              .getLatestValue(isin, from.minusDays(1))
              .ifPresent(value -> prices.put(value.date(), value.value()));
          fundValueRepository
              .findValuesBetweenDates(isin, from, to)
              .forEach(value -> prices.put(value.date(), value.value()));
          history.put(isin, prices);
        });

    return history;
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

  private Optional<BigDecimal> annualReturnRate(
      AuthenticatedPerson person, String key, LocalDate from, LocalDate to) {
    return returnsService.get(person, from, to, List.of(key)).getReturns().stream()
        .map(Returns.Return::getRate)
        .filter(Objects::nonNull)
        .findFirst();
  }
}
