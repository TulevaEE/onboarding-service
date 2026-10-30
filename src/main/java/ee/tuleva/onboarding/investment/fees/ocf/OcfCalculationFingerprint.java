package ee.tuleva.onboarding.investment.fees.ocf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class OcfCalculationFingerprint {

  private OcfCalculationFingerprint() {}

  static String of(OcfSnapshot snapshot) {
    return sha256(
        snapshot.totalOcf().stripTrailingZeros().toPlainString()
            + "|"
            + snapshot.managementFeeRate().stripTrailingZeros().toPlainString()
            + "|"
            + snapshot.complete()
            + "|"
            + snapshot.checks());
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
