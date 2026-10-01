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
          "smart.id.wrong.verification.code",
          "smart.id.unsupported.country",
          "mobile.id.cancelled",
          "mobile.id.timeout",
          "mobile.id.no.signal",
          "mobile.id.certificates.revoked",
          "mobile.id.phone.number.invalid",
          "invalid.mandate.checks.missing",
          "new.user.flow.signup.error.email.duplicate",
          "gift.amount.invalid",
          "payment.channel.invalid",
          "signature.already.signed",
          "signature.not.signed",
          "signature.not.awaited",
          "signature.session.entity.mismatch",
          "id.card.signature.invalid",
          "id.card.signing.certificate.invalid",
          "id.card.signing.certificate.mismatch",
          "id.card.signing.certificate.revoked",
          "id.card.signing.hash.function.unsupported");

  private ExpectedErrorCodes() {}

  public static boolean isExpected(@Nullable String code) {
    return code != null && EXPECTED.contains(code);
  }

  public static boolean areAllExpected(Collection<String> codes) {
    return !codes.isEmpty() && codes.stream().allMatch(ExpectedErrorCodes::isExpected);
  }
}
