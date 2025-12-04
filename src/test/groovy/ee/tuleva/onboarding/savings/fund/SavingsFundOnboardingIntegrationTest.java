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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    repository.saveOnboardingStatus(user.getId(), WHITELISTED);
  }

  @Test
  @DisplayName("KycCheckPerformedEvent with LOW risk sets status to COMPLETED")
  void onKycCheckPerformed_mapsLowRiskToCompleted() {
    var event = new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(10, LOW));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByUserId(user.getId())).contains(COMPLETED);
  }

  @Test
  @DisplayName("KycCheckPerformedEvent with MEDIUM risk sets status to PENDING")
  void onKycCheckPerformed_mapsMediumRiskToPending() {
    var event = new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(50, MEDIUM));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByUserId(user.getId())).contains(PENDING);
  }

  @Test
  @DisplayName("KycCheckPerformedEvent with HIGH risk sets status to REJECTED")
  void onKycCheckPerformed_mapsHighRiskToRejected() {
    var event = new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(99, HIGH));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByUserId(user.getId())).contains(REJECTED);
  }

  @Test
  @DisplayName("KycCheckPerformedEvent does not update status if already COMPLETED")
  void onKycCheckPerformed_doesNotUpdateIfAlreadyCompleted() {
    repository.saveOnboardingStatus(user.getId(), COMPLETED);
    var event = new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(99, HIGH));

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatusByUserId(user.getId())).contains(COMPLETED);
  }

  @Test
  @DisplayName("KycCheckPerformedEvent publishes TrackableEvent when status changes")
  void onKycCheckPerformed_publishesTrackableEventOnStatusChange() {
    var event = new KycCheckPerformedEvent(this, user.getPersonalCode(), new KycCheck(10, LOW));

    eventPublisher.publishEvent(event);

    var trackableEvents = applicationEvents.stream(TrackableEvent.class).toList();
    assertThat(trackableEvents).hasSize(1);

    var trackableEvent = trackableEvents.getFirst();
    assertThat(trackableEvent.getType()).isEqualTo(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE);
    assertThat(trackableEvent.getPerson().getPersonalCode()).isEqualTo(user.getPersonalCode());
    assertThat(trackableEvent.getData())
        .isEqualTo(Map.of("oldStatus", WHITELISTED, "newStatus", COMPLETED));
  }
}
