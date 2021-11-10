package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import java.io.Serial;
import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatedPerson implements Person, Serializable {

  @Serial private static final long serialVersionUID = 4921936099121765225L;

  @ValidPersonalCode private String personalCode;

  @NotBlank private String firstName;

  @NotBlank private String lastName;

  private String phoneNumber;

  private Long userId;

  @Override
  public String toString() {
    return personalCode;
  }
}
