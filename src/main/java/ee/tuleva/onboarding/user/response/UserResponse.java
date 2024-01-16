package ee.tuleva.onboarding.user.response;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Builder
@Getter
@Setter
public class UserResponse {

  private Long id;
  private String personalCode;
  private String firstName;
  private String lastName;
  private String email;
  private String phoneNumber;
  private Integer memberNumber;
  private String pensionAccountNumber;
  private String secondPillarPikNumber;
  private Address address;
  private boolean isSecondPillarActive;
  private boolean isThirdPillarActive;
  private PaymentRatesResponse secondPillarPaymentRates;

  public static UserResponse from(@NotNull User user) {
    return responseBuilder(user).build();
  }

  public static UserResponse from(
      @NotNull User user,
      @NotNull ContactDetails contactDetails,
      @NotNull PaymentRates paymentRates) {
    return responseBuilder(user)
        .pensionAccountNumber(contactDetails.getPensionAccountNumber())
        .address(Address.builder().countryCode(contactDetails.getCountry()).build())
        .secondPillarPikNumber(contactDetails.getActiveSecondPillarFundPik())
        .isSecondPillarActive(contactDetails.isSecondPillarActive())
        .isThirdPillarActive(contactDetails.isThirdPillarActive())
        .secondPillarPaymentRates(
            new PaymentRatesResponse(
                paymentRates.getCurrent(), paymentRates.getPending().orElse(null)))
        .build();
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
