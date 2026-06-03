package ee.tuleva.onboarding.aml.alert;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.springframework.util.DigestUtils;

/**
 * Stable dedup key for a III pillar analytics transaction. The analytics row id is NOT stable: the
 * sync deletes and re-inserts a reporting-date range on every refresh, so the same EPIS transaction
 * gets a new auto-generated id each day. Keying idempotency on a hash of the transaction's natural
 * fields keeps "alert exactly once" correct across re-syncs. (Two genuinely indistinguishable
 * transactions — same person, day, type, source, amount, account — collapse to one alert; an
 * accepted, rare trade-off vs. re-alerting daily.)
 */
final class ThirdPillarTransactionFingerprint {

  private ThirdPillarTransactionFingerprint() {}

  static String of(AnalyticsThirdPillarTransaction transaction) {
    String canonical =
        String.join(
            "|",
            nullSafe(transaction.getPersonalId()),
            nullSafe(transaction.getReportingDate()),
            nullSafe(transaction.getAccountNo()),
            nullSafe(transaction.getTransactionType()),
            nullSafe(transaction.getTransactionSource()),
            normalize(transaction.getTransactionValue()));
    return DigestUtils.md5DigestAsHex(canonical.getBytes(StandardCharsets.UTF_8));
  }

  private static String nullSafe(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String normalize(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }
}
