package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MANAGEMENT_FEE_PAYMENT_THRESHOLD;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutstandingFees {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FeeAccrualRepository feeAccrualRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final InvestmentParameterRepository investmentParameterRepository;
  private final LocalDate firstFeeMonthSettledByPayment;

  OutstandingFees(
      FeeAccrualRepository feeAccrualRepository,
      NavLedgerRepository navLedgerRepository,
      InvestmentParameterRepository investmentParameterRepository,
      @Value("${investment.fees.first-month-settled-by-payment:2026-11-01}")
          LocalDate firstFeeMonthSettledByPayment) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.navLedgerRepository = navLedgerRepository;
    this.investmentParameterRepository = investmentParameterRepository;
    this.firstFeeMonthSettledByPayment = firstFeeMonthSettledByPayment;
  }

  public static BigDecimal owed(List<FeeMonthBalance> months) {
    return months.stream().map(FeeMonthBalance::owed).reduce(ZERO, BigDecimal::add);
  }

  public BigDecimal owedOn(FeeChargedToFundPolicy.Resolver fundFeePolicy, LocalDate date) {
    return owed(monthsOn(fundFeePolicy, date));
  }

  public LocalDate firstFeeMonthSettledByPayment() {
    return firstFeeMonthSettledByPayment;
  }

  public boolean releasedAtMonthEnd(FeeType feeType, LocalDate feeMonth) {
    return switch (feeType) {
      case MANAGEMENT -> feeMonth.isBefore(firstFeeMonthSettledByPayment);
      case DEPOT -> true;
    };
  }

  public List<FeeMonthBalance> monthsOn(
      FeeChargedToFundPolicy.Resolver fundFeePolicy, LocalDate date) {
    var currentMonth = date.withDayOfMonth(1);
    var heldUntilPaid = !releasedAtMonthEnd(fundFeePolicy.feeType(), currentMonth);
    var firstOpenMonth = heldUntilPaid ? firstFeeMonthSettledByPayment : currentMonth;
    var accrualsByMonth = accrualsByMonth(fundFeePolicy, firstOpenMonth, date);
    var months =
        Stream.iterate(
                firstOpenMonth, month -> !month.isAfter(currentMonth), month -> month.plusMonths(1))
            .map(
                month ->
                    FeeMonthBalance.unpaid(
                        month,
                        roundedToCents(
                            fundFeePolicy.sumChargedDays(
                                accrualsByMonth.getOrDefault(month, Map.of())))))
            .toList();
    if (!heldUntilPaid) {
      return months;
    }
    var debits = managementFeeDebits(fundFeePolicy.fund(), firstOpenMonth.plusMonths(1), date);
    if (debits.isEmpty()) {
      return months;
    }
    return FeePaymentAllocation.allocate(months, debits, this::paymentThresholdOn);
  }

  private BigDecimal paymentThresholdOn(LocalDate bookedOn) {
    return investmentParameterRepository.findLatestValue(
        MANAGEMENT_FEE_PAYMENT_THRESHOLD, bookedOn);
  }

  private Map<LocalDate, Map<LocalDate, BigDecimal>> accrualsByMonth(
      FeeChargedToFundPolicy.Resolver fundFeePolicy, LocalDate firstMonth, LocalDate date) {
    return feeAccrualRepository
        .getAccrualByDateBetween(fundFeePolicy.fund(), fundFeePolicy.feeType(), firstMonth, date)
        .entrySet()
        .stream()
        .collect(
            groupingBy(
                entry -> entry.getKey().withDayOfMonth(1),
                toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private List<FeePayment> managementFeeDebits(TulevaFund fund, LocalDate from, LocalDate date) {
    if (from.isAfter(date)) {
      return List.of();
    }
    return navLedgerRepository
        .findEntriesByTransactionTypeBetween(
            SystemAccount.MANAGEMENT_FEE.getAccountName(fund),
            MANAGEMENT_FEE_PAYMENT,
            startOf(from),
            startOf(date.plusDays(1)))
        .stream()
        .map(
            debit ->
                new FeePayment(
                    debit.transactionDate().atZone(ESTONIAN_ZONE).toLocalDate(), debit.amount()))
        .toList();
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }

  private static BigDecimal roundedToCents(BigDecimal amount) {
    return amount.signum() == 0 ? ZERO : amount.setScale(2, HALF_UP);
  }
}
