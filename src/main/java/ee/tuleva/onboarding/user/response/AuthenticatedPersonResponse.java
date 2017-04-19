package ee.tuleva.onboarding.user.response;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AuthenticatedPersonResponse {

    private String personalCode;
    private String firstName;
    private String lastName;


    public int getAge() {
        return PersonalCode.getAge(this.getPersonalCode());
    }

    public static AuthenticatedPersonResponse fromPerson(Person authenticatedPerson) {
        return AuthenticatedPersonResponse.builder()
                .firstName(authenticatedPerson.getFirstName())
                .lastName(authenticatedPerson.getLastName())
                .personalCode(authenticatedPerson.getPersonalCode())
                .build();
    }

}
