package ee.tuleva.onboarding.user.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Email;

@Data
public class CreateUserCommand {

  @NotNull @Email private String email;

  @ValidPersonalCode private String personalCode;

  private String phoneNumber;

  @Min(18)
  @JsonIgnore
  public int getAge() {
    return PersonalCode.getAge(personalCode);
  }
}
