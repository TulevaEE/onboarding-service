package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.FormText.FUND_ISIN;
import static ee.tuleva.onboarding.insurance.FormText.FUND_NAME;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

final class TerminationStatement {

  private TerminationStatement() {}

  static String of(InsuranceTransferApplication application) {
    return "Ütlen üles täiendava kogumispensioni kindlustuslepingu nr "
        + application.request().policyNumber()
        + " "
        + when(application.request().endDate())
        + " ja soovin kasutada selle kogu tagastusväärtust pensionifondi "
        + FUND_NAME
        + " (ISIN "
        + FUND_ISIN
        + ") osakute omandamiseks, pensionikonto "
        + application.applicant().pensionAccountNumber()
        + " (KoPS § 63 lg 5, lg 5¹ p 2, lg 5² ja 5³)."
        + " Tulumaksu ei peeta kinni (TuMS § 21 lg 1¹ p 1 ja 2).";
  }

  private static String when(@Nullable LocalDate endDate) {
    return endDate == null ? "esimesel võimalusel" : "alates " + FormText.date(endDate);
  }
}
