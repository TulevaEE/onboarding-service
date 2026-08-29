package ee.tuleva.onboarding.user.response;

import static ee.tuleva.onboarding.user.Names.formatted;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.user.Emailable;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
@Setter
@Slf4j
public class UserResponse implements Person, Emailable {

  private @Nullable Long id;
  private String personalCode;
  private String firstName;
  private String lastName;
  private @Nullable String email;
  private @Nullable String phoneNumber;
  private @Nullable Integer memberNumber;
  private @Nullable String pensionAccountNumber;
  private @Nullable String secondPillarPikNumber;
  private @Nullable Country address;
  private boolean isSecondPillarActive;
  private boolean isThirdPillarActive;
  private @Nullable PaymentRatesResponse secondPillarPaymentRates;
  private @Nullable Instant memberJoinDate;
  private @Nullable Instant secondPillarOpenDate;
  private @Nullable Instant thirdPillarInitDate;
  @Nullable private Instant contactDetailsLastUpdateDate;
  private @Nullable Role role;

  public static UserResponse from(User user) {
    return responseBuilder(user).build();
  }

  public static UserResponse from(
      User user, ContactDetails contactDetails, PaymentRates paymentRates) {
    return from(user, contactDetails, paymentRates, null);
  }

  public static UserResponse from(
      User user, ContactDetails contactDetails, PaymentRates paymentRates, @Nullable Role role) {
    return responseBuilder(user)
        .role(role)
        .pensionAccountNumber(contactDetails.getPensionAccountNumber())
        .address(Country.builder().countryCode(contactDetails.getCountry()).build())
        .secondPillarPikNumber(contactDetails.getActiveSecondPillarFundPik())
        .isSecondPillarActive(contactDetails.isSecondPillarActive())
        .isThirdPillarActive(checkIfThirdPillarIsActive(contactDetails))
        .secondPillarPaymentRates(
            new PaymentRatesResponse(
                paymentRates.getCurrent(), paymentRates.getPending().orElse(null)))
        .memberJoinDate(user.getMember().map(Member::getCreatedDate).orElse(null))
        .secondPillarOpenDate(contactDetails.getSecondPillarOpenDate())
        .thirdPillarInitDate(contactDetails.getThirdPillarInitDate())
        .contactDetailsLastUpdateDate(contactDetails.getLastUpdateDate())
        .build();
  }

  private static boolean checkIfThirdPillarIsActive(ContactDetails contactDetails) {
    return contactDetails.isThirdPillarActive() || contactDetails.getThirdPillarInitDate() != null;
  }

  private static UserResponseBuilder responseBuilder(User user) {
    return builder()
        .id(user.getId())
        .firstName(formatted(user.getFirstName()))
        .lastName(formatted(user.getLastName()))
        .personalCode(user.getPersonalCode())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .memberNumber(user.getMember().map(Member::getMemberNumber).orElse(null));
  }

  public int getAge() {
    return PersonalCode.getAge(personalCode);
  }

  public int getRetirementAge() {
    return PersonalCode.getRetirementAge(personalCode);
  }

  public LocalDate getDateOfBirth() {
    return PersonalCode.getDateOfBirth(personalCode);
  }
}
