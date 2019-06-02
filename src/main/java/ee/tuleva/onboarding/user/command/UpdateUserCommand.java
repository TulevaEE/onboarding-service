package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.user.address.Address;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;

@Data
public class UpdateUserCommand {

    @NotNull
    @Email
    private String email;

    private String phoneNumber;

    private Address address;
}
