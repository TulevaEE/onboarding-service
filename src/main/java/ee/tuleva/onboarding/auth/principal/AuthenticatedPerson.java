package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatedPerson implements Person, Serializable {

  @ValidPersonalCode private String personalCode;

  @NotBlank private String firstName;

  @NotBlank private String lastName;

  private Long userId;

  @Override
  public String toString() {
    return personalCode;
  }
}
