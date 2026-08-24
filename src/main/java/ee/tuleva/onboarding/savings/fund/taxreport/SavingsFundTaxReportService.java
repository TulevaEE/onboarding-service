package ee.tuleva.onboarding.savings.fund.taxreport;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundTaxReportService {

  private final SavingsFundTransactionService savingsFundTransactionService;
  private final SavingsFundCostBasisCalculator costBasisCalculator;
  private final InvestmentAccountService investmentAccountService;

  public SavingsFundTaxReport getTaxReport(
      AuthenticatedPerson person, int year, CostBasisMethod method) {
    List<Transaction> transactions = savingsFundTransactionService.getTransactions(person);
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);

    Optional<String> declaredIban = investmentAccountService.declaredIban(person.getRoleCode());

    if (declaredIban.isEmpty()) {
      return report(year, method, gainsOf(transactions, from, to, method), null);
    }

    String iban = declaredIban.get();
    List<Transaction> investmentAccountTransactions = fundedFrom(transactions, iban, true);
    List<Transaction> ordinaryTransactions = fundedFrom(transactions, iban, false);

    if (!holdsEnoughUnits(investmentAccountTransactions)
        || !holdsEnoughUnits(ordinaryTransactions)) {
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
      InvestmentAccountGains investmentAccount) {
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
        .filter(transaction -> iban.equals(transaction.counterpartyIban()) == fromTheAccount)
        .toList();
  }

  private static boolean holdsEnoughUnits(List<Transaction> transactions) {
    BigDecimal held = BigDecimal.ZERO;

    for (Transaction transaction :
        transactions.stream().sorted(java.util.Comparator.comparing(Transaction::time)).toList()) {
      BigDecimal units = transaction.units();
      if (units == null) {
        return false;
      }
      held = transaction.isAcquisition() ? held.add(units.abs()) : held.subtract(units.abs());
      if (held.signum() < 0) {
        return false;
      }
    }

    return true;
  }

  private static BigDecimal sumOfGains(List<RealisedGain> redemptions) {
    return redemptions.stream()
        .map(RealisedGain::gain)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, HALF_UP);
  }
}
