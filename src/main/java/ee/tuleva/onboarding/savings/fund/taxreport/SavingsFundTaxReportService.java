package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundTaxReportService {

  private final SavingsFundTransactionService savingsFundTransactionService;
  private final CostBasisCalculator costBasisCalculator;

  public SavingsFundTaxReport getTaxReport(
      AuthenticatedPerson person, int year, CostBasisMethod method) {
    List<Transaction> transactions = savingsFundTransactionService.getTransactions(person);
    List<RealisedGain> redemptions =
        costBasisCalculator.realisedGainsBetween(
            transactions, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), method);

    return SavingsFundTaxReport.builder()
        .year(year)
        .method(method)
        .totalGain(sumOfGains(redemptions))
        .redemptions(redemptions)
        .build();
  }

  private static BigDecimal sumOfGains(List<RealisedGain> redemptions) {
    return redemptions.stream()
        .map(RealisedGain::gain)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, java.math.RoundingMode.HALF_UP);
  }
}
