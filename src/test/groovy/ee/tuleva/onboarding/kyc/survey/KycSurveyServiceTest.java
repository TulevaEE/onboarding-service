package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCheckService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class KycSurveyServiceTest {

  @Mock private KycSurveyRepository kycSurveyRepository;
  @Mock private KycCheckService kycCheckService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private KycSurveyService kycSurveyService;

  @Test
  @DisplayName("getCountry returns country from address answer")
  void getCountry_returnsCountryFromAddressAnswer() {
    Long userId = 1L;
    var addressDetails = new AddressDetails("Street 1", "Tallinn", "12345", "EE");
    var addressValue = new AddressValue("ADDRESS", addressDetails);
    var addressItem = new Address(addressValue);
    var survey = new KycSurveyResponse(List.of(addressItem));
    var kycSurvey = KycSurvey.builder().userId(userId).survey(survey).build();

    when(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .thenReturn(Optional.of(kycSurvey));

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).contains(new Country("EE"));
  }

  @Test
  @DisplayName("getCountry returns empty when no survey found")
  void getCountry_returnsEmptyWhenNoSurveyFound() {
    Long userId = 1L;

    when(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .thenReturn(Optional.empty());

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getCountry returns empty when survey has no address answer")
  void getCountry_returnsEmptyWhenSurveyHasNoAddressAnswer() {
    Long userId = 1L;
    var emailValue = new EmailValue("TEXT", "test@example.com");
    var emailItem = new Email(emailValue);
    var survey = new KycSurveyResponse(List.of(emailItem));
    var kycSurvey = KycSurvey.builder().userId(userId).survey(survey).build();

    when(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .thenReturn(Optional.of(kycSurvey));

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getCountry returns first address when multiple answers exist")
  void getCountry_returnsFirstAddressWhenMultipleAnswersExist() {
    Long userId = 1L;
    var emailValue = new EmailValue("TEXT", "test@example.com");
    var emailItem = new Email(emailValue);
    var addressDetails = new AddressDetails("Street 1", "Helsinki", "00100", "FI");
    var addressValue = new AddressValue("ADDRESS", addressDetails);
    var addressItem = new Address(addressValue);
    var survey = new KycSurveyResponse(List.of(emailItem, addressItem));
    var kycSurvey = KycSurvey.builder().userId(userId).survey(survey).build();

    when(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .thenReturn(Optional.of(kycSurvey));

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).contains(new Country("FI"));
  }
}
