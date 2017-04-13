package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;

public class CreateUserCommand {

  @NotBlank
  private String firstName;

  @NotBlank
  private String lastName;

  @NotNull
  @Email
  private String email;

  @ValidPersonalCode
  private String personalCode;

  private String phoneNumber;

}
