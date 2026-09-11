package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.CompanyOnboarding;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OnboardingGateTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String PERSONAL_CODE = "38888888888";

  @Mock private CompanyOnboarding companyOnboarding;
  @Mock private ApplicationEventPublisher eventPublisher;

  private OnboardingGate gate;

  @BeforeEach
  void setUp() {
    gate = new OnboardingGate(companyOnboarding, eventPublisher);
  }

  @Test
  void getOnboardingError_returnsEmptyWhenRejected() {
    given(companyOnboarding.findState(REGISTRY_CODE))
        .willReturn(Optional.of(CompanyOnboarding.State.REJECTED));

    assertThat(gate.getOnboardingError(REGISTRY_CODE)).isEmpty();
  }

  @Test
  void getOnboardingError_returnsAlreadyOnboardedErrorWhenCompleted() {
    given(companyOnboarding.findState(REGISTRY_CODE))
        .willReturn(Optional.of(CompanyOnboarding.State.COMPLETED));

    assertThat(gate.getOnboardingError(REGISTRY_CODE))
        .contains(new ValidationError("ALREADY_ONBOARDED", "Ettevõte on juba liitunud"));
  }

  @Test
  void getOnboardingError_returnsOnboardingPendingErrorWhenPending() {
    given(companyOnboarding.findState(REGISTRY_CODE))
        .willReturn(Optional.of(CompanyOnboarding.State.PENDING));

    assertThat(gate.getOnboardingError(REGISTRY_CODE))
        .contains(new ValidationError("ONBOARDING_PENDING", "Ettevõtte liitumine on pooleli"));
  }

  @Test
  void verifyOnboardingAllowed_throwsWithReasonAndAuditsWhenOnboardingPending() {
    given(companyOnboarding.findState(REGISTRY_CODE))
        .willReturn(Optional.of(CompanyOnboarding.State.PENDING));

    assertThatThrownBy(() -> gate.verifyOnboardingAllowed(REGISTRY_CODE, PERSONAL_CODE))
        .isInstanceOf(OnboardingNotAllowedException.class)
        .extracting(e -> ((OnboardingNotAllowedException) e).getReason())
        .isEqualTo(BlockedReason.ONBOARDING_PENDING);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, blockedAuditData("ONBOARDING_PENDING")));
  }

  @Test
  void verifyOnboardingAllowed_throwsAndAuditsWhenAlreadyOnboarded() {
    given(companyOnboarding.findState(REGISTRY_CODE))
        .willReturn(Optional.of(CompanyOnboarding.State.COMPLETED));

    assertThatThrownBy(() -> gate.verifyOnboardingAllowed(REGISTRY_CODE, PERSONAL_CODE))
        .isInstanceOf(OnboardingNotAllowedException.class);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, blockedAuditData("ALREADY_ONBOARDED")));
  }

  @Test
  void verifyOnboardingAllowed_doesNothingWhenRejected() {
    given(companyOnboarding.findState(REGISTRY_CODE))
        .willReturn(Optional.of(CompanyOnboarding.State.REJECTED));

    assertThatCode(() -> gate.verifyOnboardingAllowed(REGISTRY_CODE, PERSONAL_CODE))
        .doesNotThrowAnyException();
  }

  @Test
  void auditBlocked_publishesEventForNotBoardMember() {
    gate.auditBlocked(REGISTRY_CODE, PERSONAL_CODE, BlockedReason.NOT_BOARD_MEMBER);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, blockedAuditData("NOT_BOARD_MEMBER")));
  }

  private static Map<String, Object> blockedAuditData(String reason) {
    var data = new LinkedHashMap<String, Object>();
    data.put("partyType", "LEGAL_ENTITY");
    data.put("registryCode", REGISTRY_CODE);
    data.put("personalCode", PERSONAL_CODE);
    data.put("outcome", "BLOCKED");
    data.put("blockedReason", reason);
    return data;
  }
}
