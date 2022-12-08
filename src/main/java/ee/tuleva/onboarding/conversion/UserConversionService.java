package ee.tuleva.onboarding.conversion;

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING;
import static java.math.BigDecimal.ZERO;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.account.AccountStatementService;
import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.ConversionResponse.Amount;
import ee.tuleva.onboarding.conversion.ConversionResponse.Conversion;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.application.ApplicationService;
import ee.tuleva.onboarding.mandate.application.Exchange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserConversionService {

  private final AccountStatementService accountStatementService;
  private final CashFlowService cashFlowService;
  private final FundRepository fundRepository;
  private final Clock clock;
  private final ApplicationService applicationService;

  public ConversionResponse getConversion(Person person) {
    List<FundBalance> fundBalances = accountStatementService.getAccountStatement(person);
    CashFlowStatement cashFlowStatement = cashFlowService.getCashFlowStatement(person);

    return ConversionResponse.builder()
        .secondPillar(
            Conversion.builder()
                .selectionComplete(isSelectionComplete(fundBalances, 2))
                .selectionPartial(isSelectionPartial(fundBalances, 2))
                .transfersComplete(isTransfersComplete(fundBalances, 2, person))
                .transfersPartial(isTransfersPartial(fundBalances, 2, person))
                .pendingWithdrawal(applicationService.hasPendingWithdrawals(person))
                .contribution(
                    Amount.builder()
                        .yearToDate(yearToDateCashContributionSum(cashFlowStatement, 2))
                        .total(totalContributionSum(cashFlowStatement, 2))
                        .build())
                .subtraction(
                    Amount.builder()
                        .yearToDate(yearToDateSubtractionSum(cashFlowStatement, 2))
                        .total(totalSubtractionSum(cashFlowStatement, 2))
                        .build())
                .build())
        .thirdPillar(
            Conversion.builder()
                .selectionComplete(isSelectionComplete(fundBalances, 3))
                .selectionPartial(isSelectionPartial(fundBalances, 3))
                .transfersComplete(isTransfersComplete(fundBalances, 3, person))
                .transfersPartial(isTransfersPartial(fundBalances, 3, person))
                .contribution(
                    Amount.builder()
                        .yearToDate(yearToDateCashContributionSum(cashFlowStatement, 3))
                        .total(totalContributionSum(cashFlowStatement, 3))
                        .build())
                .subtraction(
                    Amount.builder()
                        .yearToDate(yearToDateSubtractionSum(cashFlowStatement, 3))
                        .total(totalSubtractionSum(cashFlowStatement, 3))
                        .build())
                .paymentComplete(paymentComplete(cashFlowStatement))
                .build())
        .build();
  }

  private boolean paymentComplete(CashFlowStatement cashFlowStatement) {
    return cashFlowStatement.getTransactions().stream()
            .filter(cashFlow -> cashFlow.isPriceTimeAfter(sameTimeLastYear()))
            .filter(CashFlow::isCashContribution)
            .filter(cashFlow -> fundRepository.findByIsin(cashFlow.getIsin()).getPillar() == 3)
            .map(CashFlow::getAmount)
            .reduce(ZERO, BigDecimal::add)
            .compareTo(ZERO)
        > 0;
  }

  private BigDecimal yearToDateCashContributionSum(
      CashFlowStatement cashFlowStatement, int pillar) {
    return sum(
        cashFlowStatement,
        pillar,
        cashFlow ->
            cashFlow.isCashContribution() && cashFlow.isPriceTimeAfter(lastDayOfLastYear()));
  }

  private BigDecimal totalContributionSum(CashFlowStatement cashFlowStatement, int pillar) {
    return sum(cashFlowStatement, pillar, CashFlow::isContribution);
  }

  private BigDecimal yearToDateSubtractionSum(CashFlowStatement cashFlowStatement, int pillar) {
    return sum(
        cashFlowStatement,
        pillar,
        cashFlow -> cashFlow.isSubtraction() && cashFlow.isPriceTimeAfter(lastDayOfLastYear()));
  }

  private BigDecimal totalSubtractionSum(CashFlowStatement cashFlowStatement, int pillar) {
    return sum(cashFlowStatement, pillar, CashFlow::isSubtraction);
  }

  private BigDecimal sum(
      CashFlowStatement cashFlowStatement, int pillar, Predicate<CashFlow> filterBy) {
    return cashFlowStatement.getTransactions().stream()
        .filter(filterBy)
        .filter(
            cashFlow -> {
              Fund fund = fundRepository.findByIsin(cashFlow.getIsin()); // TODO: O(n) queries
              if (fund == null) {
                log.error("We didn't find the fund source: " + cashFlow.getIsin());
                return false;
              } else {
                return fund.getPillar() == pillar;
              }
            })
        .map(CashFlow::getAmount)
        .reduce(ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private Instant lastDayOfLastYear() {
    return ZonedDateTime.now(clock).minusYears(1).with(lastDayOfYear()).toInstant();
  }

  private Instant sameTimeLastYear() {
    return ZonedDateTime.now(clock).minusYears(1).toInstant();
  }

  private boolean isSelectionComplete(List<FundBalance> fundBalances, Integer pillar) {
    return filter(fundBalances, pillar).findFirst().isPresent()
        && filter(fundBalances, pillar).anyMatch(FundBalance::isActiveContributions)
        && filter(fundBalances, pillar)
            .filter(FundBalance::isActiveContributions)
            .allMatch(FundBalance::isOwnFund);
  }

  private boolean isSelectionPartial(List<FundBalance> fundBalances, Integer pillar) {
    return filter(fundBalances, pillar).findFirst().isPresent()
        && filter(fundBalances, pillar).anyMatch(FundBalance::isActiveContributions)
        && filter(fundBalances, pillar)
            .filter(FundBalance::isActiveContributions)
            .anyMatch(FundBalance::isOwnFund);
  }

  private Stream<FundBalance> filter(List<FundBalance> fundBalances, Integer pillar) {
    return fundBalances.stream().filter(fundBalance -> pillar.equals(fundBalance.getPillar()));
  }

  private boolean isTransfersComplete(
      List<FundBalance> fundBalances, Integer pillar, Person person) {
    return getIsinsOfFullPendingTransfersToConvertedFundManager(person, fundBalances, pillar)
        .containsAll(unConvertedIsins(fundBalances, pillar));
  }

  private boolean isTransfersPartial(
      List<FundBalance> fundBalances, Integer pillar, Person person) {
    return filter(fundBalances, pillar).findFirst().isEmpty()
        || filter(fundBalances, pillar)
            .anyMatch(fundBalance -> fundBalance.isOwnFund() && fundBalance.hasValue())
        || hasAnyPendingTransfersToOwnFunds(person, pillar);
  }

  private boolean hasAnyPendingTransfersToOwnFunds(Person person, Integer pillar) {
    var pendingTransferApplications = applicationService.getTransferApplications(PENDING, person);
    return pendingTransferApplications.stream()
        .filter(application -> pillar.equals(application.getPillar()))
        .flatMap(application -> application.getDetails().getExchanges().stream())
        .anyMatch(Exchange::isToOwnFund);
  }

  private Set<String> getIsinsOfFullPendingTransfersToConvertedFundManager(
      Person person, List<FundBalance> fundBalances, Integer pillar) {
    var pendingTransferApplications = applicationService.getTransferApplications(PENDING, person);
    return pendingTransferApplications.stream()
        .filter(application -> pillar.equals(application.getPillar()))
        .flatMap(application -> application.getDetails().getExchanges().stream())
        .filter(exchange -> exchange.isToOwnFund() && amountMatches(exchange, fundBalances))
        .map(exchange -> exchange.getSourceFund().getIsin())
        .collect(toSet());
  }

  private boolean amountMatches(Exchange exchange, List<FundBalance> fundBalances) {
    if (exchange.getPillar() == 2) {
      return exchange.isFullAmount();
    }
    if (exchange.getPillar() == 3) {
      FundBalance fundBalance = fundBalance(exchange, fundBalances);
      return exchange.getAmount().equals(fundBalance.getTotalValue());
    }
    throw new IllegalStateException("Invalid pillar: " + exchange.getPillar());
  }

  private FundBalance fundBalance(Exchange exchange, List<FundBalance> fundBalances) {
    return fundBalances.stream()
        .filter(fundBalance -> exchange.getSourceFund().getIsin().equals(fundBalance.getIsin()))
        .findFirst()
        .orElse(FundBalance.builder().build());
  }

  private List<String> unConvertedIsins(List<FundBalance> fundBalances, Integer pillar) {
    return filter(fundBalances, pillar)
        .filter(
            fundBalance ->
                !fundBalance.isOwnFund()
                    && fundBalance.hasValue()
                    && !fundBalance.isExitRestricted())
        .map(fundBalance -> fundBalance.getFund().getIsin())
        .collect(toList());
  }
}
