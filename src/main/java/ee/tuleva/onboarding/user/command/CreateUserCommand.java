package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import lombok.Data;
import org.hibernate.validator.constraints.Email;

import javax.validation.constraints.NotNull;

@Data
public class CreateUserCommand {

  @NotNull
  @Email
  private String email;

  @ValidPersonalCode
  private String personalCode;

  private String phoneNumber;

}
