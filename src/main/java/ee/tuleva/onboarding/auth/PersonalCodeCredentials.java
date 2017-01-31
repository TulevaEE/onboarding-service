package ee.tuleva.onboarding.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
@AllArgsConstructor
public class PersonalCodeCredentials {
    @NotNull
    @Size(min = 11, max = 11)
    private String idCode;

    @Override
    public String toString() {
        return "PersonalCodeCredentials(" +
            "personalCode=" + getIdCode() +
            ")";
    }
}
