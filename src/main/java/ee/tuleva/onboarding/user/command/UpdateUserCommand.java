package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.user.address.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserCommand {

  @NotNull @Email private String email;

  private String phoneNumber;

  @Valid private Address address;
}
