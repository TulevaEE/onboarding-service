package ee.tuleva.onboarding.kyc.survey;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.AddressDetails;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PepStatus;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@JsonInclude(NON_NULL)
public record KycIdentityResponse(
    @Nullable List<String> citizenship,
    @Nullable Address address,
    @Nullable String email,
    @Nullable String phoneNumber,
    @Nullable PepStatus pepSelfDeclaration,
    @Nullable Instant updatedAt) {

  public boolean isComplete() {
    return citizenship != null
        && !citizenship.isEmpty()
        && address != null
        && pepSelfDeclaration != null
        && email != null;
  }

  public record Address(String street, String city, String postalCode, String countryCode) {

    static Address from(AddressDetails details) {
      return new Address(
          details.street(), details.city(), details.postalCode(), details.countryCode());
    }
  }

  static KycIdentityResponse empty(User user) {
    return new KycIdentityResponse(null, null, user.getEmail(), user.getPhoneNumber(), null, null);
  }

  static KycIdentityResponse from(KycSurveyResponse survey, Instant updatedAt, User user) {
    return new KycIdentityResponse(
        survey.citizenship().orElse(null),
        survey.address().map(Address::from).orElse(null),
        user.getEmail(),
        user.getPhoneNumber(),
        survey.pepSelfDeclaration().orElse(null),
        updatedAt);
  }
}
