package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.emptyMandate;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch;
import static ee.tuleva.onboarding.notification.email.EmailStatus.SCHEDULED;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailTest {

  private static final String PERSONAL_CODE = "38888888888";

  @Test
  void toStringKeepsCorrelationFieldsButNeverThePersonalCode() {
    Mandate mandate = emptyMandate().user(sampleUser().personalCode(PERSONAL_CODE).build()).build();
    MandateBatch mandateBatch = aSavedMandateBatch(List.of(mandate));
    mandate.setMandateBatch(mandateBatch);
    Email email =
        Email.builder()
            .id(123L)
            .personalCode(PERSONAL_CODE)
            .mandrillMessageId("abc-123")
            .type(THIRD_PILLAR_PAYMENT_REMINDER_MANDATE)
            .status(SCHEDULED)
            .mandateBatchId(mandateBatch.getId())
            .build();

    assertThat(email.toString())
        .doesNotContain(PERSONAL_CODE)
        .contains(
            "id=123",
            "mandrillMessageId=abc-123",
            "type=THIRD_PILLAR_PAYMENT_REMINDER_MANDATE",
            "status=SCHEDULED");
  }

  @Test
  void isTodayIsTrueWhenCreatedOnTheSameDayAsTheClock() {
    Instant now = Instant.parse("2026-08-28T10:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    Email email = Email.builder().personalCode(PERSONAL_CODE).createdDate(now).build();

    assertThat(email.isToday(clock)).isTrue();
  }

  @Test
  void isTodayIsFalseWhenCreatedOnADifferentDayThanTheClock() {
    Instant createdDate = Instant.parse("2026-08-27T23:59:00Z");
    Instant now = Instant.parse("2026-08-28T00:01:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    Email email = Email.builder().personalCode(PERSONAL_CODE).createdDate(createdDate).build();

    assertThat(email.isToday(clock)).isFalse();
  }
}
