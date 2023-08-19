package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import lombok.*;

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

  @Builder.Default private Map<String, Serializable> attributes = new HashMap<>();

  private Long userId;

  @Override
  public String toString() {
    return personalCode;
  }

  public <T extends Serializable> T getAttribute(String attribute) {
    return (T) attributes.get(attribute);
  }
}
