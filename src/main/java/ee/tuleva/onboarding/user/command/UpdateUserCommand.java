package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.user.address.Address;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserCommand {

  @NotNull @Email private String email;

  private String phoneNumber;

  private Address address;
}
