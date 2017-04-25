package ee.tuleva.onboarding.user.response;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.text.WordUtils;

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

    public int getAge() {
        return PersonalCode.getAge(personalCode);
    }

    public static UserResponse fromUser(User user) {
        return builder()
            .id(user.getId())
            .firstName(capitalize(user.getFirstName()))
            .lastName(capitalize(user.getLastName()))
            .personalCode(user.getPersonalCode())
            .email(user.getEmail())
            .phoneNumber(user.getPhoneNumber())
            .build();
    }

    private static String capitalize(String string) {
        return WordUtils.capitalizeFully(string, ' ', '-');
    }
}
