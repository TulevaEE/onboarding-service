package ee.tuleva.onboarding.user.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class CreateUserCommand {

  @NotNull
  @Email
  private String email;

  @ValidPersonalCode
  private String personalCode;

  private String phoneNumber;

  @Min(18)
  @JsonIgnore
  public int getAge() {
    return PersonalCode.getAge(personalCode);
  }

}
