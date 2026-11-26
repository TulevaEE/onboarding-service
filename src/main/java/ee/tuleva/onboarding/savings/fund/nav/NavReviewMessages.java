package ee.tuleva.onboarding.savings.fund.nav;

import java.time.LocalDate;

final class NavReviewMessages {

  private NavReviewMessages() {}

  static String held(NavCalculationResult result, String reason, ReviewEmail reviewEmail) {
    return """
        🔴 NAV report HELD for review — NOT sent to SEB.
        %s
        %s
        Until it is released or passes the check, the NAV is recalculated every 5–15 minutes and this message repeats.

        What to do now:
        1. Verify the NAV in the "%s NAV arvutamine ..." email (funds@tuleva.ee) reconciles with the SEB custodian report.
        2. If correct, release it. The release sends the report to SEB; do not forward the email by hand.
        %s
        3. Optional (needs compliance sign-off) — widen the TD gate going forward:
           INSERT INTO investment_parameter (parameter_name, fund_code, effective_date, numeric_value) VALUES ('TRACKING_BREACH_THRESHOLD', NULL, CURRENT_DATE, 0.005);"""
        .formatted(reason, reviewEmail.csvLine(), result.fund().getCode(), howToRelease(result));
  }

  static String publicNavChanged(
      NavCalculationResult result, String reason, ReviewEmail reviewEmail) {
    return """
        🔴 %s NAV recalculated to a value other than the public one — held for review, NOT sent to SEB.
        %s
        %s
        Issuing and redemptions price at the public NAV in index_values, which is append-only.
        Until it is corrected or released, the NAV is recalculated every 5–15 minutes and this message repeats.

        What to do now:
        1. Check the recalculated NAV against the SEB custodian report.
        2. If it is correct, correct the NAV and AUM of navDate=%s in index_values by hand. The next recalculation then goes to SEB on its own.
        3. Or release it as it is. The release sends the report to SEB and leaves index_values unchanged.
        %s"""
        .formatted(
            result.fund().getCode(),
            reason,
            reviewEmail.csvLine(),
            result.positionReportDate(),
            howToRelease(result));
  }

  static String revised(NavCalculationResult result, String reason, ReviewEmail reviewEmail) {
    String fundCode = result.fund().getCode();
    String savingsFundStep =
        result.fund().isSavingsFund()
            ? "\n4. %s only: the public NAV in index_values is append-only and still holds the earlier value; correct it manually."
                .formatted(fundCode)
            : "";
    return """
        🔴 NAV REVISED after the custodian position report changed — held for review, NOT sent to SEB.
        fund=%s, navDate=%s: %s
        %s

        What to do now:
        1. Check the changed rows against the new SEB position report.
        2. If correct, release it. The release sends the revised report to SEB; do not forward the email by hand.
        %s
        3. Until it is released, onboarding-service reads only a NAV SEB has received.%s"""
        .formatted(
            fundCode,
            result.positionReportDate(),
            reason,
            reviewEmail.csvLine(),
            howToRelease(result),
            savingsFundStep);
  }

  private static String howToRelease(NavCalculationResult result) {
    String fundCode = result.fund().getCode();
    LocalDate navDate = result.positionReportDate();
    return ("   agent-gateway nav_release (two ✅): fund=%s, navDate=%s\n"
            + "   Until agent-gateway has nav_release: GET /admin/nav/%s/%s shows the values to"
            + " approve; POST navPerUnit, aum and approvedBy to /admin/nav/%s/%s/release")
        .formatted(fundCode, navDate, fundCode, navDate, fundCode, navDate);
  }
}
