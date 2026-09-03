package ee.tuleva.onboarding.error;

import java.util.Collection;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ExpectedErrorCodes {

  private static final Set<String> EXPECTED =
      Set.of(
          "smart.id.user.refused",
          "smart.id.account.not.found",
          "smart.id.timeout",
          "smart.id.unsupported.country",
          "id.card.document.type.not.allowed",
          "mobile.id.cancelled",
          "mobile.id.timeout",
          "mobile.id.no.signal",
          "mobile.id.certificates.revoked",
          "invalid.mandate.checks.missing",
          "new.user.flow.signup.error.email.duplicate");

  private ExpectedErrorCodes() {}

  public static boolean isExpected(@Nullable String code) {
    return code != null && EXPECTED.contains(code);
  }

  public static boolean areAllExpected(Collection<String> codes) {
    return !codes.isEmpty() && codes.stream().allMatch(ExpectedErrorCodes::isExpected);
  }
}
