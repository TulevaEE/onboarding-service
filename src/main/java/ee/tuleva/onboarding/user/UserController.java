package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.user.command.UpdateUserCommand;
import ee.tuleva.onboarding.user.preferences.CsdUserPreferencesService;
import ee.tuleva.onboarding.user.preferences.UserPreferences;
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

	private final CsdUserPreferencesService preferencesService;
	private final UserService userService;

	@ApiOperation(value = "Get info about the current user")
	@GetMapping("/me")
	public UserResponse me(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
		Long userId = authenticatedPerson.getUserId();
		User user = userService.getById(userId);
		return UserResponse.fromUser(user);
	}

	@ApiOperation(value = "Update the current user")
	@PatchMapping("/me")
	public UserResponse patchMe(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
								@Valid @RequestBody UpdateUserCommand cmd,
								@ApiIgnore Errors errors) throws ValidationErrorsException {

		if (errors != null && errors.hasErrors()) {
			throw new ValidationErrorsException(errors);
		}

		User user = userService.updateUser(authenticatedPerson.getPersonalCode(), cmd.getEmail(), cmd.getPhoneNumber());

		return UserResponse.fromUser(user);
	}

	@ApiOperation(value = "Get info about the current user preferences from CSD")
	@GetMapping("/preferences")
	public UserPreferences getPreferences(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
		return preferencesService.getPreferences(authenticatedPerson.getPersonalCode());
	}

}