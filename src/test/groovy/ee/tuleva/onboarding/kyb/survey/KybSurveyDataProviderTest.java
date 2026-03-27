package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSourceItem;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanySourceOfIncome;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KybSurveyDataProviderTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final Long USER_ID = 1L;
  private static final String PERSONAL_CODE = "38501010002";

  private final KybSurveyRepository kybSurveyRepository = mock(KybSurveyRepository.class);
  private final KybSurveyResponseMapper kybSurveyResponseMapper = new KybSurveyResponseMapper();
  private final UserRepository userRepository = mock(UserRepository.class);

  private final KybSurveyDataProvider provider =
      new KybSurveyDataProvider(kybSurveyRepository, kybSurveyResponseMapper, userRepository);

  @Test
  void returnsPersonalCodeAndSelfCertificationFromLatestSurvey() {
    var surveyResponse = surveyResponseWithAllCertifications();
    var survey =
        KybSurvey.builder()
            .userId(USER_ID)
            .registryCode(REGISTRY_CODE)
            .survey(surveyResponse)
            .build();
    given(kybSurveyRepository.findTopByRegistryCodeOrderByCreatedTimeDesc(REGISTRY_CODE))
        .willReturn(Optional.of(survey));
    given(userRepository.findById(USER_ID))
        .willReturn(Optional.of(User.builder().personalCode(PERSONAL_CODE).build()));

    var result = provider.getLatestByRegistryCode(REGISTRY_CODE);

    assertThat(result.personalCode()).isEqualTo(new PersonalCode(PERSONAL_CODE));
    assertThat(result.selfCertification()).isEqualTo(new SelfCertification(true, true, true));
  }

  @Test
  void throwsWhenNoSurveyFound() {
    given(kybSurveyRepository.findTopByRegistryCodeOrderByCreatedTimeDesc(REGISTRY_CODE))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> provider.getLatestByRegistryCode(REGISTRY_CODE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenUserNotFound() {
    var survey =
        KybSurvey.builder()
            .userId(USER_ID)
            .registryCode(REGISTRY_CODE)
            .survey(surveyResponseWithAllCertifications())
            .build();
    given(kybSurveyRepository.findTopByRegistryCodeOrderByCreatedTimeDesc(REGISTRY_CODE))
        .willReturn(Optional.of(survey));
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> provider.getLatestByRegistryCode(REGISTRY_CODE))
        .isInstanceOf(IllegalStateException.class);
  }

  private static KybSurveyResponse surveyResponseWithAllCertifications() {
    return new KybSurveyResponse(
        List.of(
            new CompanySourceOfIncome(
                List.of(
                    new CompanyIncomeSourceItem.Option(ONLY_ACTIVE_IN_ESTONIA),
                    new CompanyIncomeSourceItem.Option(
                        NOT_SANCTIONED_NOT_PROFITING_FROM_SANCTIONED_COUNTRIES),
                    new CompanyIncomeSourceItem.Option(NOT_IN_CRYPTO)))));
  }
}
