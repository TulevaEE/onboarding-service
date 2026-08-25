package ee.tuleva.onboarding.savings.fund.taxreport;

import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
    List<Transaction> transactions = savingsFundTransactionService.getTransactions(person);
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    Optional<String> declaredIban = investmentAccountService.declaredIban(person.getRoleCode());

    if (declaredIban.isEmpty()) {
      return report(year, method, gainsOf(transactions, from, to, method), null);
    }

    String iban = IbanValidator.canonicalize(declaredIban.get());
    List<Transaction> investmentAccountTransactions = fundedFrom(transactions, iban, true);
    List<Transaction> ordinaryTransactions = fundedFrom(transactions, iban, false);

    if (!canBeSplit(transactions, investmentAccountTransactions, ordinaryTransactions, to)) {
      return report(
          year,
          method,
          gainsOf(transactions, from, to, method),
          InvestmentAccountGains.builder()
              .iban(iban)
              .totalGain(BigDecimal.ZERO.setScale(2, HALF_UP))
              .redemptions(List.of())
              .redeemedOutsideTheAccount(true)
              .build());
    }

    List<RealisedGain> investmentAccountGains =
        gainsOf(investmentAccountTransactions, from, to, method);

    return report(
        year,
        method,
        gainsOf(ordinaryTransactions, from, to, method),
        InvestmentAccountGains.builder()
            .iban(iban)
            .totalGain(sumOfGains(investmentAccountGains))
            .redemptions(investmentAccountGains)
            .redeemedOutsideTheAccount(false)
            .build());
  }

  private List<RealisedGain> gainsOf(
      List<Transaction> transactions, LocalDate from, LocalDate to, CostBasisMethod method) {
    return costBasisCalculator.realisedGainsBetween(transactions, from, to, method);
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

  private static List<Transaction> fundedFrom(
      List<Transaction> transactions, String iban, boolean fromTheAccount) {
    return transactions.stream()
        .filter(transaction -> facedTheAccount(transaction, iban) == fromTheAccount)
        .toList();
  }

  private static boolean facedTheAccount(Transaction transaction, String canonicalIban) {
    String counterpartyIban = transaction.counterpartyIban();
    return counterpartyIban != null
        && canonicalIban.equals(IbanValidator.canonicalize(counterpartyIban));
  }

  private static boolean canBeSplit(
      List<Transaction> transactions,
      List<Transaction> investmentAccountTransactions,
      List<Transaction> ordinaryTransactions,
      LocalDate to) {
    return transactions.stream().noneMatch(transaction -> transaction.counterpartyIban() == null)
        && holdsEnoughUnits(upTo(investmentAccountTransactions, to))
        && holdsEnoughUnits(upTo(ordinaryTransactions, to));
  }

  private static List<Transaction> upTo(List<Transaction> transactions, LocalDate to) {
    return transactions.stream()
        .filter(transaction -> !dayOf(transaction.time()).isAfter(to))
        .toList();
  }

  private static LocalDate dayOf(Instant time) {
    return time.atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private static boolean holdsEnoughUnits(List<Transaction> transactions) {
    BigDecimal held = BigDecimal.ZERO;

    for (Transaction transaction :
        transactions.stream().sorted(comparing(Transaction::time)).toList()) {
      BigDecimal units = requireUnits(transaction);
      held = transaction.isAcquisition() ? held.add(units.abs()) : held.subtract(units.abs());
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
