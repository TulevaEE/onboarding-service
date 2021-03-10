package ee.tuleva.onboarding.conversion;

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.ZERO;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static java.util.stream.Collectors.toList;

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
import ee.tuleva.onboarding.mandate.transfer.TransferExchange;
import ee.tuleva.onboarding.mandate.transfer.TransferExchangeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
  private final TransferExchangeService transferExchangeService;
  private final CashFlowService cashFlowService;
  private final FundRepository fundRepository;
  private final Clock clock;

  private static final String CONVERTED_FUND_MANAGER_NAME = "Tuleva";
  public static final String EXIT_RESTRICTED_FUND = "EE3600109484";

  public ConversionResponse getConversion(Person person) {
    List<FundBalance> fundBalances = accountStatementService.getAccountStatement(person);
    CashFlowStatement cashFlowStatement = cashFlowService.getCashFlowStatement(person);

    return ConversionResponse.builder()
        .secondPillar(
            Conversion.builder()
                .selectionComplete(isSelectionComplete(fundBalances, 2))
                .transfersComplete(isTransfersComplete(fundBalances, 2, person))
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
                .transfersComplete(isTransfersComplete(fundBalances, 3, person))
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
            .filter(cashFlow -> cashFlow.getDate().isAfter(sameTimeLastYear()))
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
        cashFlow -> cashFlow.isCashContribution() && cashFlow.isAfter(lastDayOfLastYear()));
  }

  private BigDecimal totalContributionSum(CashFlowStatement cashFlowStatement, int pillar) {
    return sum(cashFlowStatement, pillar, CashFlow::isContribution);
  }

  private BigDecimal yearToDateSubtractionSum(CashFlowStatement cashFlowStatement, int pillar) {
    return sum(
        cashFlowStatement,
        pillar,
        cashFlow -> cashFlow.isSubtraction() && cashFlow.isAfter(lastDayOfLastYear()));
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
        .setScale(2, ROUND_HALF_UP);
  }

  private LocalDate lastDayOfLastYear() {
    return sameTimeLastYear().with(lastDayOfYear());
  }

  private LocalDate sameTimeLastYear() {
    return LocalDate.now(clock).minusYears(1);
  }

  private boolean isSelectionComplete(List<FundBalance> fundBalances, Integer pillar) {
    return filter(fundBalances, pillar).findFirst().isPresent()
        && filter(fundBalances, pillar).anyMatch(FundBalance::isActiveContributions)
        && filter(fundBalances, pillar)
            .filter(FundBalance::isActiveContributions)
            .allMatch(
                fundBalance ->
                    fundBalance
                        .getFund()
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME));
  }

  private Stream<FundBalance> filter(List<FundBalance> fundBalances, Integer pillar) {
    return fundBalances.stream().filter(fundBalance -> pillar.equals(fundBalance.getPillar()));
  }

  private boolean isTransfersComplete(
      List<FundBalance> fundBalances, Integer pillar, Person person) {
    return getIsinsOfFullPendingTransfersToConvertedFundManager(person, fundBalances, pillar)
        .containsAll(unConvertedIsins(fundBalances, pillar));
  }

  private List<String> getIsinsOfFullPendingTransfersToConvertedFundManager(
      Person person, List<FundBalance> fundBalances, Integer pillar) {
    return getPendingTransfers(person).stream()
        .filter(transferExchange -> pillar.equals(transferExchange.getPillar()))
        .filter(
            transferExchange ->
                transferExchange
                        .getTargetFund()
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                    && amountMatches(transferExchange, fundBalances))
        .map(transferExchange -> transferExchange.getSourceFund().getIsin())
        .collect(toList());
  }

  private boolean amountMatches(TransferExchange transferExchange, List<FundBalance> fundBalances) {
    if (transferExchange.getPillar() == 2) {
      return transferExchange.getAmount().intValue() == 1;
    }
    if (transferExchange.getPillar() == 3) {
      FundBalance fundBalance = fundBalance(transferExchange, fundBalances);
      return transferExchange.getAmount().equals(fundBalance.getTotalValue());
    }
    throw new IllegalStateException("Invalid pillar: " + transferExchange.getPillar());
  }

  private FundBalance fundBalance(
      TransferExchange transferExchange, List<FundBalance> fundBalances) {
    return fundBalances.stream()
        .filter(
            fundBalance -> transferExchange.getSourceFund().getIsin().equals(fundBalance.getIsin()))
        .findFirst()
        .orElse(FundBalance.builder().build());
  }

  private List<TransferExchange> getPendingTransfers(Person person) {
    return transferExchangeService.get(person).stream()
        .filter(transferExchange -> transferExchange.getStatus().equals(PENDING))
        .collect(toList());
  }

  private List<String> unConvertedIsins(List<FundBalance> fundBalances, Integer pillar) {
    return fundBalances.stream()
        .filter(fundBalance -> pillar.equals(fundBalance.getPillar()))
        .filter(
            fundBalance ->
                !fundBalance
                        .getFund()
                        .getFundManager()
                        .getName()
                        .equalsIgnoreCase(CONVERTED_FUND_MANAGER_NAME)
                    && fundBalance.getValue().compareTo(ZERO) > 0
                    && !EXIT_RESTRICTED_FUND.equals(fundBalance.getIsin()))
        .map(fundBalance -> fundBalance.getFund().getIsin())
        .collect(toList());
  }
}
