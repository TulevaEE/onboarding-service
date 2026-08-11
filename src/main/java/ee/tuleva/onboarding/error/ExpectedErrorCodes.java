package ee.tuleva.onboarding.error;

import java.util.Set;

public final class ExpectedErrorCodes {

  private static final Set<String> EXPECTED =
      Set.of(
          "smart.id.user.refused",
          "smart.id.account.not.found",
          "mobile.id.cancelled",
          "mobile.id.timeout",
          "mobile.id.no.signal",
          "mobile.id.certificates.revoked",
          "invalid.mandate.checks.missing");

  private ExpectedErrorCodes() {}

  public static boolean isExpected(String code) {
    return EXPECTED.contains(code);
  }
}
