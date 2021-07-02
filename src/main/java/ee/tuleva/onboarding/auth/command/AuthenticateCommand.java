package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
@Builder
public class AuthenticateCommand {

  @NotBlank
  @Length(min = 7, max = 30)
  @Pattern(regexp = "^\\+?\\d{7,30}$")
  private String phoneNumber;

  @ValidPersonalCode private String personalCode;

  @NotNull
  private AuthenticationType type;
}
