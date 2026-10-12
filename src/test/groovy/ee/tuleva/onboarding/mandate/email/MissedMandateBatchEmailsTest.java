package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.WITHDRAWAL;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.notification.email.EmailType.WITHDRAWAL_BATCH;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchStatus;
import ee.tuleva.onboarding.mandate.processor.MandateProcess;
import ee.tuleva.onboarding.mandate.processor.MandateProcessErrorResolver;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  MissedMandateBatchEmails.class,
  EmailPersistenceService.class,
  MandateProcessorService.class,
  MandateProcessErrorResolver.class,
  MissedMandateBatchEmailsTest.FixedClock.class
})
class MissedMandateBatchEmailsTest {

  private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
  private static final Instant TWO_DAYS_AGO = NOW.minus(Duration.ofDays(2));

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, UTC);
    }
  }

  @Autowired private TestEntityManager entityManager;
  @Autowired private MissedMandateBatchEmails missedMandateBatchEmails;
  @Autowired private EmailPersistenceService emailPersistenceService;

  @MockitoBean private MandateBatchEmailService mandateBatchEmailService;
  @MockitoBean private EmailService emailService;
  @MockitoBean private MandateGateway mandateGateway;

  private User owner;

  @BeforeEach
  void persistOwner() {
    owner = entityManager.persist(sampleUser().id(null).member(null).build());
  }

  @AfterEach
  void restoreClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void findsASignedBatchFromWithinTheLookbackThatPensionikeskusAcceptedAndThatHasNoEmail() {
    var missed = batch(SIGNED, TWO_DAYS_AGO, true);

    assertThat(missedMandateBatchEmails.find(7)).containsExactly(missed.getId());
  }

  @Test
  void skipsABatchThatAlreadyHasItsEmail() {
    var emailed = batch(SIGNED, TWO_DAYS_AGO, true);
    emailPersistenceService.saveWithMandateBatch(
        owner, "a-message-id", WITHDRAWAL_BATCH, "sent", emailed.getId());

    assertThat(missedMandateBatchEmails.find(7)).isEmpty();
  }

  @Test
  void skipsABatchOlderThanTheLookback() {
    batch(SIGNED, NOW.minus(Duration.ofDays(8)), true);

    assertThat(missedMandateBatchEmails.find(7)).isEmpty();
  }

  @Test
  void skipsABatchTheProcessingPollerMayStillBeCompleting() {
    batch(SIGNED, NOW.minus(Duration.ofMinutes(5)), true);

    assertThat(missedMandateBatchEmails.find(7)).isEmpty();
  }

  @Test
  void skipsABatchThatWasNeverSigned() {
    batch(INITIALIZED, TWO_DAYS_AGO, true);

    assertThat(missedMandateBatchEmails.find(7)).isEmpty();
  }

  @Test
  void skipsABatchPensionikeskusRejected() {
    batch(SIGNED, TWO_DAYS_AGO, false);

    assertThat(missedMandateBatchEmails.find(7)).isEmpty();
  }

  @Test
  void skipsASignedBatchThatNeverReachedPensionikeskus() {
    signedBatchNeverSubmitted(TWO_DAYS_AGO);

    assertThat(missedMandateBatchEmails.find(7)).isEmpty();
  }

  @Test
  void resendEmailsEachMissedBatchToItsOwnerInEstonian() {
    var missed = batch(SIGNED, TWO_DAYS_AGO, true);
    willAnswer(invocation -> emailSaved(missed))
        .given(mandateBatchEmailService)
        .sendMandateBatch(any(), any(), any());

    var resend = missedMandateBatchEmails.resend(7);

    assertThat(resend).isEqualTo(new MissedEmailResend(List.of(missed.getId()), List.of()));
    then(mandateBatchEmailService)
        .should()
        .sendMandateBatch(
            argThat(user -> user.getId().equals(owner.getId())),
            argThat(batch -> batch.getId().equals(missed.getId())),
            eq(Locale.of("et")));
  }

  @Test
  void resendReportsABatchWhoseEmailThrowsAsFailedAndCarriesOnWithTheNext() {
    var throwing = batch(SIGNED, TWO_DAYS_AGO, true);
    var next = batch(SIGNED, TWO_DAYS_AGO.plus(Duration.ofHours(1)), true);
    willThrow(new IllegalStateException("Mandrill unavailable"))
        .given(mandateBatchEmailService)
        .sendMandateBatch(any(), argThat(batch -> batch.getId().equals(throwing.getId())), any());
    willAnswer(invocation -> emailSaved(next))
        .given(mandateBatchEmailService)
        .sendMandateBatch(any(), argThat(batch -> batch.getId().equals(next.getId())), any());

    var resend = missedMandateBatchEmails.resend(7);

    assertThat(resend)
        .isEqualTo(new MissedEmailResend(List.of(next.getId()), List.of(throwing.getId())));
  }

  @Test
  void resendReportsABatchThatStillHasNoEmailAfterSendingAsFailed() {
    var unsent = batch(SIGNED, TWO_DAYS_AGO, true);

    var resend = missedMandateBatchEmails.resend(7);

    assertThat(resend).isEqualTo(new MissedEmailResend(List.of(), List.of(unsent.getId())));
  }

  private Object emailSaved(MandateBatch batch) {
    return emailPersistenceService.saveWithMandateBatch(
        owner, "a-message-id", WITHDRAWAL_BATCH, "sent", batch.getId());
  }

  private MandateBatch batch(
      MandateBatchStatus status, Instant createdAt, boolean acceptedByPensionikeskus) {
    var mandate = persistedMandateInBatch(status, createdAt);
    entityManager.persist(
        MandateProcess.builder()
            .mandate(mandate)
            .processId(UUID.randomUUID().toString())
            .type(WITHDRAWAL)
            .successful(acceptedByPensionikeskus)
            .build());
    entityManager.flush();
    entityManager.clear();
    return mandate.getMandateBatch();
  }

  private MandateBatch signedBatchNeverSubmitted(Instant createdAt) {
    var mandate = persistedMandateInBatch(SIGNED, createdAt);
    entityManager.flush();
    entityManager.clear();
    return mandate.getMandateBatch();
  }

  private Mandate persistedMandateInBatch(MandateBatchStatus status, Instant createdAt) {
    ClockHolder.setClock(Clock.fixed(createdAt, UTC));
    var mandate = samplePartialWithdrawalMandate();
    mandate.setId(null);
    mandate.setUser(owner);
    var batch = MandateBatch.builder().status(status).mandates(List.of(mandate)).build();
    mandate.setMandateBatch(batch);
    entityManager.persist(mandate);
    return mandate;
  }
}
