package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatedPerson implements Person, Serializable {

    @ValidPersonalCode
    private String personalCode;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private Long userId;

}
