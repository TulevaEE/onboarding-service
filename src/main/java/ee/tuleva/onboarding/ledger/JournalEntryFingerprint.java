package ee.tuleva.onboarding.ledger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class JournalEntryFingerprint {

  static final String METADATA_KEY = "fingerprint";

  private JournalEntryFingerprint() {}

  static String of(JournalEntryPart part) {
    return sha256(part.date() + "|" + part.documentType() + "|" + normalizedLines(part));
  }

  private static String normalizedLines(JournalEntryPart part) {
    return part.lines().stream()
        .map(line -> line.accountCode() + "=" + line.amount().stripTrailingZeros().toPlainString())
        .sorted()
        .collect(joining("|"));
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
