package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import lombok.Data;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;

@Data
public class UpdateUserCommand {

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
