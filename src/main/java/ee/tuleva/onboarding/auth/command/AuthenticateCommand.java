package ee.tuleva.onboarding.auth.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticateCommand {

  @Length(min = 7, max = 30)
  @Pattern(regexp = "^\\+?\\d{7,30}$")
  private String phoneNumber;

  @ValidPersonalCode private String personalCode;

  @NotNull private AuthenticationType type;
}
