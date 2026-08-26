package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.savings.fund.taxreport.TransactionOrder.ACQUISITIONS_FIRST_WITHIN_AN_INSTANT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import ee.tuleva.onboarding.savings.fund.TransactionsWithCounterparties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsFundTaxReportService {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final SavingsFundTransactionService savingsFundTransactionService;
  private final SavingsFundCostBasisCalculator costBasisCalculator;
  private final InvestmentAccountService investmentAccountService;

  @Transactional(readOnly = true)
  public SavingsFundTaxReport getTaxReport(
      AuthenticatedPerson person, int year, CostBasisMethod method) {
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    Optional<String> declared = investmentAccountService.declaredIban(person.getRoleCode());

    if (declared.isEmpty()) {
      return report(
          year,
          method,
          costBasisCalculator.realisedGainsBetween(
              savingsFundTransactionService.getTransactions(person), from, to, method),
          null);
    }

    TransactionsWithCounterparties withCounterparties =
        savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person);
    List<Transaction> transactions = withCounterparties.transactions();
    Map<UUID, String> counterpartyIbans = withCounterparties.counterpartyIbans();

    String iban = IbanValidator.canonicalize(declared.get());
    List<Transaction> fromTheAccount =
        transactions.stream()
            .filter(transaction -> facedTheAccount(transaction, iban, counterpartyIbans))
            .toList();
    List<Transaction> ordinary =
        transactions.stream()
            .filter(transaction -> !facedTheAccount(transaction, iban, counterpartyIbans))
            .toList();

    if (!canBeSplit(transactions, counterpartyIbans, fromTheAccount, ordinary, to)) {
      return report(
          year,
          method,
          costBasisCalculator.realisedGainsBetween(transactions, from, to, method),
          InvestmentAccountGains.builder().build());
    }

    List<RealisedGain> gainsFromTheAccount =
        costBasisCalculator.realisedGainsBetween(fromTheAccount, from, to, method);

    return report(
        year,
        method,
        costBasisCalculator.realisedGainsBetween(ordinary, from, to, method),
        InvestmentAccountGains.builder().totalGain(sumOfGains(gainsFromTheAccount)).build());
  }

  private static SavingsFundTaxReport report(
      int year,
      CostBasisMethod method,
      List<RealisedGain> redemptions,
      @Nullable InvestmentAccountGains investmentAccount) {
    return SavingsFundTaxReport.builder()
        .year(year)
        .method(method)
        .totalGain(sumOfGains(redemptions))
        .redemptions(redemptions)
        .investmentAccount(investmentAccount)
        .build();
  }

  private static boolean facedTheAccount(
      Transaction transaction, String iban, Map<UUID, String> counterpartyIbans) {
    String counterpartyIban = counterpartyIbans.get(transaction.id());
    return counterpartyIban != null && iban.equals(IbanValidator.canonicalize(counterpartyIban));
  }

  private static boolean canBeSplit(
      List<Transaction> transactions,
      Map<UUID, String> counterpartyIbans,
      List<Transaction> fromTheAccount,
      List<Transaction> ordinary,
      LocalDate to) {
    return transactions.stream()
            .filter(transaction -> happenedBy(transaction, to))
            .allMatch(transaction -> cameFromAKnownAccount(transaction, counterpartyIbans))
        && holdsEnoughUnits(fromTheAccount, to)
        && holdsEnoughUnits(ordinary, to);
  }

  private static boolean cameFromAKnownAccount(
      Transaction transaction, Map<UUID, String> counterpartyIbans) {
    String counterpartyIban = counterpartyIbans.get(transaction.id());
    return counterpartyIban != null && IbanValidator.isValid(counterpartyIban);
  }

  private static boolean happenedBy(Transaction transaction, LocalDate to) {
    return !transaction.time().atZone(ESTONIAN_ZONE).toLocalDate().isAfter(to);
  }

  private static boolean holdsEnoughUnits(List<Transaction> transactions, LocalDate to) {
    BigDecimal held = BigDecimal.ZERO;

    for (Transaction transaction :
        transactions.stream().sorted(ACQUISITIONS_FIRST_WITHIN_AN_INSTANT).toList()) {
      if (!happenedBy(transaction, to)) {
        continue;
      }
      BigDecimal units = requireUnits(transaction).abs();
      held = transaction.isAcquisition() ? held.add(units) : held.subtract(units);
      if (held.signum() < 0) {
        return false;
      }
    }

    return true;
  }

  private static BigDecimal requireUnits(Transaction transaction) {
    BigDecimal units = transaction.units();

    if (units == null) {
      throw new IllegalStateException(
          "Savings fund transaction missing units: id=%s, time=%s"
              .formatted(transaction.id(), transaction.time()));
    }

    return units;
  }

  private static BigDecimal sumOfGains(List<RealisedGain> redemptions) {
    return redemptions.stream()
        .map(RealisedGain::gain)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, HALF_UP);
  }
}
