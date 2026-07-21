package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.KybCheckOverrideRepository;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualCompanyOnboardingServiceTest {

  private static final String REGISTRY_CODE = "12934765";
  private static final String PERSONAL_CODE = "38901040329";
  private static final String REASON = "single shareholder, two spousal beneficial owners";

  @Mock private KybSurveyRepository kybSurveyRepository;
  @Mock private KybCheckOverrideRepository kybCheckOverrideRepository;
  @Mock private LegalEntityScreener legalEntityScreener;
  @Mock private UserRepository userRepository;

  @InjectMocks private ManualCompanyOnboardingService service;

  @Test
  void onboard_savesSelfCertifiedSurveyAndOverrideThenReScreens() {
    var user = mock(User.class);
    given(user.getId()).willReturn(42L);
    given(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE))
        .willReturn(List.of(boardMemberRelationship(PERSONAL_CODE)));
    given(userRepository.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.of(user));
    given(
            kybCheckOverrideRepository.findByRegistryCodeAndCheckType(
                REGISTRY_CODE, SINGLE_BOARD_MEMBER_OWNERSHIP))
        .willReturn(Optional.empty());

    service.onboard(REGISTRY_CODE, PERSONAL_CODE, List.of(SINGLE_BOARD_MEMBER_OWNERSHIP), REASON);

    verify(kybSurveyRepository)
        .save(
            argThat(
                survey ->
                    survey.getUserId().equals(42L)
                        && survey.getRegistryCode().equals(REGISTRY_CODE)
                        && new KybSurveyResponseMapper()
                            .extractSelfCertification(survey.getSurvey())
                            .equals(new SelfCertification(true, true, true))));
    verify(kybCheckOverrideRepository)
        .save(
            argThat(
                override ->
                    override.getRegistryCode().equals(REGISTRY_CODE)
                        && override.getCheckType() == SINGLE_BOARD_MEMBER_OWNERSHIP
                        && override.isForcedSuccess()
                        && override.getReason().equals(REASON)));
    verify(legalEntityScreener).screenLatest(REGISTRY_CODE);
  }

  @Test
  void onboard_throwsAndDoesNothingWhenPersonIsNotABoardMember() {
    given(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE))
        .willReturn(List.of(boardMemberRelationship("11111111111")));

    assertThatThrownBy(
            () ->
                service.onboard(
                    REGISTRY_CODE, PERSONAL_CODE, List.of(SINGLE_BOARD_MEMBER_OWNERSHIP), REASON))
        .isInstanceOf(NotBoardMemberException.class);

    verify(kybSurveyRepository, never()).save(any());
    verify(kybCheckOverrideRepository, never()).save(any());
    verify(legalEntityScreener, never()).screenLatest(any());
  }

  private static CompanyRelationship boardMemberRelationship(String personalCode) {
    return new CompanyRelationship(
        "NATURAL_PERSON",
        "JUHL",
        "Board member",
        "Jaan",
        "Tamm",
        personalCode,
        null,
        null,
        null,
        null,
        null,
        "EE");
  }
}
