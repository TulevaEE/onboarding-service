package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.REJOIN;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandateWithPaymentRate;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleRejoinCancellationMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleRejoinMandate;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.processor.MandateProcess;
import ee.tuleva.onboarding.mandate.processor.MandateProcessErrorResolver;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  RejoinCancellations.class,
  MandateProcessorService.class,
  MandateProcessErrorResolver.class
})
class RejoinCancellationsTest {

  private static final Instant MIDNIGHT_THE_REJOIN_IS_DATED = Instant.parse("2026-11-01T22:00:00Z");
  private static final Instant LATER_THAT_DAY =
      MIDNIGHT_THE_REJOIN_IS_DATED.plus(Duration.ofHours(12));

  @Autowired private TestEntityManager entityManager;
  @Autowired private RejoinCancellations rejoinCancellations;

  @MockitoBean private MandateGateway mandateGateway;

  private final ApplicationSnapshot rejoin =
      ApplicationSnapshot.builder()
          .id(321L)
          .date(MIDNIGHT_THE_REJOIN_IS_DATED)
          .status(PENDING)
          .type(REJOIN)
          .build();

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
  void aCancellationPensionikeskusAcceptedCancelsIt() {
    processedAs(signedCancellation(owner, LATER_THAT_DAY), true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isTrue();
  }

  @Test
  void aCancellationPensionikeskusAcceptedAfterTheRejoinWasFiledThroughUsCancelsIt() {
    processedAs(rejoinedThroughASignedBatch(owner, LATER_THAT_DAY), true);
    processedAs(signedCancellation(owner, LATER_THAT_DAY.plus(Duration.ofHours(1))), true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isTrue();
  }

  @Test
  void aCancellationPensionikeskusHasNotAnsweredYetKeepsItPending() {
    processedAs(signedCancellation(owner, LATER_THAT_DAY), null);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void aSignedCancellationWhoseProcessingNeverStartedKeepsItPending() {
    signedCancellation(owner, LATER_THAT_DAY);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void aCancellationPensionikeskusRejectedKeepsItPending() {
    processedAs(signedCancellation(owner, LATER_THAT_DAY), false);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void anUnsignedCancellationDraftKeepsItPending() {
    persisted(owner, sampleRejoinCancellationMandate(), LATER_THAT_DAY, false);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void rejoiningAgainTheSameDayAfterTheCancellationKeepsTheNewRejoinPending() {
    processedAs(signedCancellation(owner, LATER_THAT_DAY), true);
    processedAs(rejoinedThroughASignedBatch(owner, LATER_THAT_DAY.plus(Duration.ofHours(1))), true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void aRejoinPensionikeskusRejectedAfterTheCancellationKeepsItCanceled() {
    processedAs(signedCancellation(owner, LATER_THAT_DAY), true);
    processedAs(
        rejoinedThroughASignedBatch(owner, LATER_THAT_DAY.plus(Duration.ofHours(1))), false);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isTrue();
  }

  @Test
  void anotherApplicationPensionikeskusAcceptedAfterTheCancellationKeepsItCanceled() {
    processedAs(signedCancellation(owner, LATER_THAT_DAY), true);
    processedAs(
        persisted(
            owner, sampleMandateWithPaymentRate(), LATER_THAT_DAY.plus(Duration.ofHours(1)), true),
        true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isTrue();
  }

  @Test
  void aCancellationOfAnEarlierWindowCreatedBeforeTheRejoinWasFiledKeepsItPending() {
    processedAs(
        signedCancellation(owner, MIDNIGHT_THE_REJOIN_IS_DATED.minus(Duration.ofDays(30))), true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void anotherPersonsCancellationKeepsItPending() {
    var someoneElse =
        entityManager.persist(
            sampleUser()
                .id(null)
                .member(null)
                .personalCode("38501010002")
                .email("someone.else@example.com")
                .build());
    processedAs(signedCancellation(someoneElse, LATER_THAT_DAY), true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  @Test
  void theRejoinMandateItselfKeepsItPending() {
    processedAs(rejoinedThroughASignedBatch(owner, LATER_THAT_DAY), true);

    assertThat(rejoinCancellations.isCanceled(ownerAsPerson(), rejoin)).isFalse();
  }

  private AuthenticatedPerson ownerAsPerson() {
    return authenticatedPersonFromUser(owner).build();
  }

  private Mandate signedCancellation(User user, Instant createdAt) {
    return persisted(user, sampleRejoinCancellationMandate(), createdAt, true);
  }

  private Mandate rejoinedThroughASignedBatch(User user, Instant createdAt) {
    ClockHolder.setClock(Clock.fixed(createdAt, UTC));
    var batch = MandateBatch.builder().status(SIGNED).mandates(List.of()).build();
    batch.setFile("signed".getBytes());
    var rejoinMandate = sampleRejoinMandate();
    rejoinMandate.setMandateBatch(entityManager.persist(batch));
    return persisted(user, rejoinMandate, createdAt, false);
  }

  private Mandate persisted(User user, Mandate mandate, Instant createdAt, boolean signed) {
    ClockHolder.setClock(Clock.fixed(createdAt, UTC));
    mandate.setId(null);
    mandate.setUser(user);
    mandate.setMandate(signed ? "signed".getBytes() : null);
    return entityManager.persistAndFlush(mandate);
  }

  private void processedAs(Mandate mandate, @Nullable Boolean acceptedByPensionikeskus) {
    entityManager.persistAndFlush(
        MandateProcess.builder()
            .mandate(mandate)
            .processId(UUID.randomUUID().toString())
            .type(mandate.getDetails().getApplicationType())
            .successful(acceptedByPensionikeskus)
            .build());
  }
}
