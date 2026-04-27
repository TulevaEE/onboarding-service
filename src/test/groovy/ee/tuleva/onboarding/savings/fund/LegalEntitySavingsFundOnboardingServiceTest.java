package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_REGISTRY_CODE;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.company.BoardMembershipService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegalEntitySavingsFundOnboardingServiceTest {

  private static final String PERSONAL_CODE = "38812121215";

  @Mock private BoardMembershipService boardMembershipService;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @InjectMocks private LegalEntitySavingsFundOnboardingService service;

  @Test
  void getOnboardingStatus_returnsStatusForBoardMember() {
    given(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .willReturn(true);
    given(savingsFundOnboardingRepository.findStatus(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(Optional.of(COMPLETED));

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .hasValue(COMPLETED);
  }

  @Test
  void getOnboardingStatus_returnsEmptyWhenNoStatus() {
    given(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .willReturn(true);
    given(savingsFundOnboardingRepository.findStatus(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(Optional.empty());

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isEmpty();
  }

  @Test
  void getOnboardingStatus_returnsEmptyWhenNotBoardMember() {
    given(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .willReturn(false);

    assertThat(service.getOnboardingStatus(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isEmpty();
  }

  @Test
  void isOnboardingCompleted_returnsTrueForCompletedBoardMember() {
    given(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .willReturn(true);
    given(savingsFundOnboardingRepository.isOnboardingCompleted(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(true);

    assertThat(service.isOnboardingCompleted(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isTrue();
  }

  @Test
  void isOnboardingCompleted_returnsFalseForNonBoardMember() {
    given(boardMembershipService.isBoardMember(PERSONAL_CODE, SAMPLE_REGISTRY_CODE))
        .willReturn(false);

    assertThat(service.isOnboardingCompleted(PERSONAL_CODE, SAMPLE_REGISTRY_CODE)).isFalse();
  }
}
