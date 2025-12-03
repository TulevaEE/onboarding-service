package ee.tuleva.onboarding.user.response;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.notification.email.Emailable;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Builder
@Getter
@Setter
@Slf4j
public class UserResponse implements Person, Emailable {

  private Long id;
  private String personalCode;
  private String firstName;
  private String lastName;
  private String email;
  private String phoneNumber;
  private Integer memberNumber;
  private String pensionAccountNumber;
  private String secondPillarPikNumber;
  private Country address;
  private boolean isSecondPillarActive;
  private boolean isThirdPillarActive;
  private PaymentRatesResponse secondPillarPaymentRates;
  private Instant memberJoinDate;
  private Instant secondPillarOpenDate;
  private Instant thirdPillarInitDate;
  @Nullable private Instant contactDetailsLastUpdateDate;

  public static UserResponse from(@NotNull User user) {
    return responseBuilder(user).build();
  }

  public static UserResponse from(
      @NotNull User user,
      @NotNull ContactDetails contactDetails,
      @NotNull PaymentRates paymentRates) {
    return responseBuilder(user)
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

  private static boolean checkIfThirdPillarIsActive(@NotNull ContactDetails contactDetails) {
    return contactDetails.isThirdPillarActive() || contactDetails.getThirdPillarInitDate() != null;
  }

  private static UserResponseBuilder responseBuilder(@NotNull User user) {
    return builder()
        .id(user.getId())
        .firstName(capitalize(user.getFirstName()))
        .lastName(capitalize(user.getLastName()))
        .personalCode(user.getPersonalCode())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .memberNumber(user.getMember().map(Member::getMemberNumber).orElse(null));
  }

  private static String capitalize(String string) {
    return capitalizeFully(string, ' ', '-');
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
