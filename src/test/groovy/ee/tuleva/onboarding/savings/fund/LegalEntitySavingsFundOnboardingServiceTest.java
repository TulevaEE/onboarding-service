package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.aml.OnboardingReview.Outcome.COMPLETE;
import static ee.tuleva.onboarding.aml.OnboardingReview.Outcome.REJECT;
import static ee.tuleva.onboarding.company.CompanyFixture.SAMPLE_REGISTRY_CODE;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.ON_HOLD;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.company.BoardMembershipService;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.RegistryCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LegalEntitySavingsFundOnboardingServiceTest {

  private static final String PERSONAL_CODE = "38812121215";

  @Mock private BoardMembershipService boardMembershipService;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
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

  @Test
  void applyReview_completesAHeldCompanyTheReviewerAcceptedAndAnnouncesIt() {
    given(savingsFundOnboardingRepository.findStatus(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(Optional.of(ON_HOLD));
    given(
            savingsFundOnboardingRepository.changeStatus(
                SAMPLE_REGISTRY_CODE, LEGAL_ENTITY, ON_HOLD, COMPLETED, AML_SERVICE_REVIEW))
        .willReturn(true);
    given(companyRepository.findByRegistryCode(SAMPLE_REGISTRY_CODE))
        .willReturn(
            Optional.of(
                Company.builder().registryCode(SAMPLE_REGISTRY_CODE).name("Test OÜ").build()));

    service.applyReview(SAMPLE_REGISTRY_CODE, COMPLETE);

    then(eventPublisher)
        .should()
        .publishEvent(
            argThat(
                (LegalEntityOnboardedEvent event) ->
                    event.getCompany().registryCode().equals(new RegistryCode(SAMPLE_REGISTRY_CODE))
                        && event.getCompany().name().equals("Test OÜ")));
  }

  @Test
  void applyReview_rejectsAHeldCompanyTheReviewerRefused() {
    given(savingsFundOnboardingRepository.findStatus(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(Optional.of(ON_HOLD));
    given(
            savingsFundOnboardingRepository.changeStatus(
                SAMPLE_REGISTRY_CODE, LEGAL_ENTITY, ON_HOLD, REJECTED, AML_SERVICE_REVIEW))
        .willReturn(true);

    service.applyReview(SAMPLE_REGISTRY_CODE, REJECT);

    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void applyReview_announcesNothingWhenTheStatusChangedMeanwhile() {
    given(savingsFundOnboardingRepository.findStatus(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(Optional.of(ON_HOLD));
    given(
            savingsFundOnboardingRepository.changeStatus(
                SAMPLE_REGISTRY_CODE, LEGAL_ENTITY, ON_HOLD, COMPLETED, AML_SERVICE_REVIEW))
        .willReturn(false);

    service.applyReview(SAMPLE_REGISTRY_CODE, COMPLETE);

    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void applyReview_leavesAPendingOrRejectedCompanyAloneUnlessARejectedOneIsAccepted() {
    given(savingsFundOnboardingRepository.findStatus(SAMPLE_REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(Optional.of(PENDING), Optional.of(REJECTED));

    service.applyReview(SAMPLE_REGISTRY_CODE, COMPLETE);
    service.applyReview(SAMPLE_REGISTRY_CODE, REJECT);

    then(savingsFundOnboardingRepository)
        .should(never())
        .changeStatus(any(), any(), any(), any(), any());
    then(eventPublisher).shouldHaveNoInteractions();
  }
}
