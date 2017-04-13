package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.User;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Optional;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatedPerson implements Person, Serializable {

    @NotBlank
    @Size(min = 11, max = 11)
    private String personalCode;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private User user;

    public Optional<User> getUser() {
        return Optional.ofNullable(user);
    }

}
