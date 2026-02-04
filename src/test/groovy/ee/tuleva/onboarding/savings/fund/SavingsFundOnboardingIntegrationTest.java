package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@RecordApplicationEvents
class SavingsFundOnboardingIntegrationTest {

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private SavingsFundOnboardingRepository repository;
  @Autowired private UserRepository userRepository;
  @Autowired private ApplicationEvents applicationEvents;

  private User user;

  @BeforeEach
  void setUp() {
    user = userRepository.save(sampleUserNonMember().personalCode("39802077017").id(null).build());
  }

  @Test
  void onKycCheckPerformed_mapsNoneRiskToCompleted() {
    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(NONE, Map.of()));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode())).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_mapsLowRiskToCompleted() {
    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(LOW, Map.of()));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode())).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_mapsMediumRiskToPending() {
    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(MEDIUM, Map.of()));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode())).contains(PENDING);
  }

  @Test
  void onKycCheckPerformed_mapsHighRiskToRejected() {
    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(HIGH, Map.of()));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode())).contains(REJECTED);
  }

  @Test
  void onKycCheckPerformed_doesNotUpdateIfAlreadyCompleted() {
    repository.saveOnboardingStatus(user.getPersonalCode(), COMPLETED);
    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(HIGH, Map.of()));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode())).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_createsRecordWhenNoneExists() {
    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode()))
        .isEqualTo(Optional.empty());

    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(LOW, Map.of()));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByPersonalCode(user.getPersonalCode())).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_publishesTrackableEventOnStatusChange() {
    var event =
        new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(LOW, Map.of()));

    eventPublisher.publishEvent(event);

    var trackableEvents = applicationEvents.stream(TrackableEvent.class).toList();
    assertThat(trackableEvents).hasSize(1);

    var trackableEvent = trackableEvents.getFirst();
    assertThat(trackableEvent.getType()).isEqualTo(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE);
    assertThat(trackableEvent.getPerson().getPersonalCode()).isEqualTo(user.getPersonalCode());
    assertThat(trackableEvent.getData()).isEqualTo(Map.of("newStatus", COMPLETED));
  }
}
