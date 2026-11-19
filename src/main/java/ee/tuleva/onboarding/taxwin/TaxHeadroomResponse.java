package ee.tuleva.onboarding.taxwin;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

record TaxHeadroomResponse(
    int year,
    BigDecimal knownIncome,
    @Nullable YearMonth incomeKnownThrough,
    List<ExpectedMonthlyIncome> expectedIncome,
    ExpectedIncomeBasis expectedIncomeBasis,
    BigDecimal paid,
    int monthsLeft,
    int limit,
    LimitCertainty limitCertainty,
    int remaining,
    int refund,
    int monthlyPayment,
    PaymentSuggestion paymentSuggestion) {

  static TaxHeadroomResponse of(
      int year, IncomeEstimate estimate, BigDecimal paid, int monthsLeft, HeadroomFigures figures) {
    return new TaxHeadroomResponse(
        year,
        estimate.knownIncome(),
        estimate.incomeKnownThrough(),
        estimate.expectedIncome(),
        estimate.basis(),
        paid,
        monthsLeft,
        figures.limit(),
        figures.limitCertainty(),
        figures.remaining(),
        figures.refund(),
        figures.monthlyPayment(),
        figures.paymentSuggestion());
  }
}
