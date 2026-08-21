package ee.tuleva.onboarding.kyc;

import static ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.survey.KycSurvey;
import ee.tuleva.onboarding.kyc.survey.KycSurveyRepository;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponse;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KycCountryServiceTest {

  private static final Long USER_ID = 1L;

  @Mock private KycSurveyRepository kycSurveyRepository;

  @InjectMocks private KycCountryService kycCountryService;

  @Test
  void getCountries_returnsCountryFromAddressAnswer() {
    givenLatestSurvey(address("EE"));

    Optional<Set<Country>> result = kycCountryService.getCountries(USER_ID);

    assertThat(result).contains(Countries.of("EE"));
  }

  @Test
  void getCountries_returnsResidenceAndEveryCitizenship() {
    givenLatestSurvey(
        new Citizenship(new CountriesValue("COUNTRIES", List.of("RU"))), address("EE"));

    Optional<Set<Country>> result = kycCountryService.getCountries(USER_ID);

    assertThat(result).contains(Countries.of("EE", "RU"));
  }

  @Test
  void getCountries_returnsFirstAddressWhenMultipleAnswersExist() {
    givenLatestSurvey(
        new Email(new EmailValue("TEXT", "test@example.com")), address("FI"), address("EE"));

    Optional<Set<Country>> result = kycCountryService.getCountries(USER_ID);

    assertThat(result).contains(Countries.of("FI"));
  }

  @Test
  void getCountries_returnsEmptyWhenNoSurveyFound() {
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.empty());

    Optional<Set<Country>> result = kycCountryService.getCountries(USER_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void getCountries_returnsEmptyWhenSurveyHasNoAddressAnswer() {
    givenLatestSurvey(new Email(new EmailValue("TEXT", "test@example.com")));

    Optional<Set<Country>> result = kycCountryService.getCountries(USER_ID);

    assertThat(result).isEmpty();
  }

  private Address address(String countryCode) {
    return new Address(
        new AddressValue(
            "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", countryCode)));
  }

  private void givenLatestSurvey(KycSurveyResponseItem... answers) {
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(
            Optional.of(
                KycSurvey.builder()
                    .userId(USER_ID)
                    .survey(new KycSurveyResponse(List.of(answers)))
                    .build()));
  }
}
