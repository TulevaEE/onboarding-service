package ee.tuleva.onboarding.taxwin;

import java.time.YearMonth;
import java.util.Map;

public interface TaxCertificateClient {

  Map<YearMonth, DeclaredMonth> declaredIncome(
      String personalCode, String consentReference, IncomeWindow window);
}
