package ee.tuleva.onboarding.nudge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public enum ExperimentArm {
  CONTROL,
  TREATMENT;

  private static final int BUCKETS = 100;
  private static final int HASH_PREFIX_LENGTH = 8;

  static ExperimentArm assign(String personalCode, String seed, int holdoutPercent) {
    return bucket(personalCode, seed) < holdoutPercent ? CONTROL : TREATMENT;
  }

  static long bucket(String personalCode, String seed) {
    String hash = md5(personalCode.trim() + seed);
    return Long.parseLong(hash.substring(0, HASH_PREFIX_LENGTH), 16) % BUCKETS;
  }

  private static String md5(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is unavailable in this runtime", e);
    }
  }
}
