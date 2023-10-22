package ee.tuleva.onboarding.comparisons.overview;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountOverviewProvider {

  private final FundRepository fundRepository;
  private final CashFlowService cashFlowService;

  public AccountOverview getAccountOverview(Person person, Instant startTime, Integer pillar) {
    Instant endTime = Instant.now();
    CashFlowStatement cashFlowStatement =
        cashFlowService.getCashFlowStatement(person, toLocalDate(startTime), toLocalDate(endTime));

    Predicate<CashFlow> pillarFilter = createPillarFilter(pillar);

    BigDecimal beginningBalance = convertBalance(cashFlowStatement.getStartBalance(), pillarFilter);
    BigDecimal endingBalance = convertBalance(cashFlowStatement.getEndBalance(), pillarFilter);
    List<Transaction> transactions =
        convertTransactions(cashFlowStatement.getTransactions(), pillarFilter);

    return AccountOverview.builder()
        .beginningBalance(beginningBalance)
        .endingBalance(endingBalance)
        .transactions(transactions)
        .startTime(startTime)
        .endTime(endTime)
        .pillar(pillar)
        .build();
  }

  private Predicate<CashFlow> createPillarFilter(Integer pillar) {
    List<String> pillarIsins =
        fundRepository.findAllByPillar(pillar).stream().map(Fund::getIsin).toList();
    return cashFlow -> pillarIsins.contains(cashFlow.getIsin());
  }

  private BigDecimal convertBalance(
      Map<String, CashFlow> balance, Predicate<CashFlow> cashFlowFilter) {
    return balance.values().stream()
        .filter(cashFlowFilter)
        .map(CashFlow::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<Transaction> convertTransactions(
      List<CashFlow> cashFlows, Predicate<CashFlow> cashFlowFilter) {
    return cashFlows.stream()
        .filter(cashFlowFilter)
        .map(cashFlow -> new Transaction(cashFlow.getAmount(), cashFlow.getPriceTime()))
        .collect(toList());
  }

  private LocalDate toLocalDate(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate();
  }
}
