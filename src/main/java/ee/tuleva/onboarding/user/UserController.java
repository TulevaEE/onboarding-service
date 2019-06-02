package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.user.address.AddressService;
import ee.tuleva.onboarding.user.command.CreateUserCommand;
import ee.tuleva.onboarding.user.command.UpdateUserCommand;
import ee.tuleva.onboarding.user.response.UserResponse;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class UserController {

    private final UserService userService;
    private final EpisService episService;
    private final AddressService addressService;

    @ApiOperation(value = "Get info about the current user")
    @GetMapping("/me")
    public UserResponse me(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
        Long userId = authenticatedPerson.getUserId();
        User user = userService.getById(userId);
        UserPreferences contactDetails = episService.getContactDetails(authenticatedPerson);
        return UserResponse.fromUser(user, contactDetails);
    }

    @ApiOperation(value = "Update the current user")
    @PatchMapping("/me")
    public UserResponse patchMe(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                @Valid @RequestBody UpdateUserCommand cmd,
                                @ApiIgnore Errors errors) throws ValidationErrorsException {

        if (errors != null && errors.hasErrors()) {
            throw new ValidationErrorsException(errors);
        }

        User user = userService.updateUser(
            authenticatedPerson.getPersonalCode(),
            cmd.getEmail(),
            cmd.getPhoneNumber());

        UserPreferences contactDetails = addressService.updateAddress(authenticatedPerson, cmd.getAddress());

        return UserResponse.fromUser(user, contactDetails);
    }

    @ApiOperation(value = "Create a new user")
    @PostMapping("/users")
    public UserResponse createUser(@Valid @RequestBody CreateUserCommand cmd,
                                   @ApiIgnore Errors errors) throws ValidationErrorsException {

        if (errors != null && errors.hasErrors()) {
            throw new ValidationErrorsException(errors);
        }

        User user = userService.createOrUpdateUser(cmd.getPersonalCode(), cmd.getEmail(), cmd.getPhoneNumber());

        return UserResponse.fromUser(user);
    }
}