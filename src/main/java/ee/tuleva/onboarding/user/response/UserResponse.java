package ee.tuleva.onboarding.user.response;

import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.text.WordUtils;
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
    private Address address;

    public int getAge() {
        return PersonalCode.getAge(personalCode);
    }

    public static UserResponse fromUser(@NotNull User user) {
        return responseBuilder(user).build();
    }

    public static UserResponse fromUser(@NotNull User user, UserPreferences contactDetails) {
        return responseBuilder(user)
            .pensionAccountNumber(contactDetails.getPensionAccountNumber())
            .address(Address.builder()
                .street(contactDetails.getAddressRow1())
                .districtCode(contactDetails.getDistrictCode())
                .postalCode(contactDetails.getPostalIndex())
                .countryCode(contactDetails.getCountry())
                .build())
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
        return WordUtils.capitalizeFully(string, ' ', '-');
    }
}
