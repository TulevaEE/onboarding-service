package ee.tuleva.onboarding.user.response;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.PersonalCode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class UserResponse {

    private Long id;
    private String personalCode;
    private String firstName;
    private String lastName;

    public int getAge() {
        return PersonalCode.getAge(this.getPersonalCode());
    }

    public static UserResponse fromAuthenticatedPerson(AuthenticatedPerson authenticatedPerson) {
        return UserResponse.builder()
                .id(authenticatedPerson.getUser().getId())
                .firstName(authenticatedPerson.getFirstName())
                .lastName(authenticatedPerson.getLastName())
                .personalCode(authenticatedPerson.getPersonalCode())
                .build();
    }

}
