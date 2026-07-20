package ee.tuleva.onboarding.error;

import static ee.tuleva.onboarding.error.SentryErrorCodeFingerprint.ERROR_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class SentryErrorCodeFingerprintTest {

  private final SentryErrorCodeFingerprint fingerprint = new SentryErrorCodeFingerprint();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void groupsEventsByTheErrorCodeInMdcWithoutRelyingOnSentryContextTagConfiguration() {
    MDC.put(ERROR_CODE, "payment.amount.required");
    var event = new SentryEvent();

    var result = fingerprint.execute(event, new Hint());

    assertThat(result.getFingerprints())
        .isEqualTo(List.of("{{ default }}", "payment.amount.required"));
  }

  @Test
  void tagsTheEventWithTheErrorCodeSoItIsSearchableInSentry() {
    MDC.put(ERROR_CODE, "payment.amount.required");
    var event = new SentryEvent();

    var result = fingerprint.execute(event, new Hint());

    assertThat(result.getTag(ERROR_CODE)).isEqualTo("payment.amount.required");
  }

  @Test
  void groupsByAnErrorCodeAlreadyPromotedToATagWhenMdcIsEmpty() {
    var event = new SentryEvent();
    event.setTag(ERROR_CODE, "payment.channel.not.supported");

    var result = fingerprint.execute(event, new Hint());

    assertThat(result.getFingerprints())
        .isEqualTo(List.of("{{ default }}", "payment.channel.not.supported"));
  }

  @Test
  void keepsDistinctErrorCodesInDistinctGroups() {
    MDC.put(ERROR_CODE, "payment.amount.required");
    var required = new SentryEvent();
    fingerprint.execute(required, new Hint());

    MDC.put(ERROR_CODE, "payment.channel.not.supported");
    var unsupported = new SentryEvent();
    fingerprint.execute(unsupported, new Hint());

    assertThat(required.getFingerprints()).isNotEqualTo(unsupported.getFingerprints());
  }

  @Test
  void leavesEventsWithoutAnErrorCodeToSentryDefaultGrouping() {
    var event = new SentryEvent();

    var result = fingerprint.execute(event, new Hint());

    assertThat(result.getFingerprints()).isNull();
    assertThat(result.getTag(ERROR_CODE)).isNull();
  }
}
