package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.response.AuthenticatedPersonResponse;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class UserController {

	private final CsdUserPreferencesService preferencesService;

	@ApiOperation(value = "Get info about the current user")
	@RequestMapping(method = GET, value = "/me")
	public AuthenticatedPersonResponse me(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
		return AuthenticatedPersonResponse.fromAuthenticatedPerson(authenticatedPerson);
	}

	@ApiOperation(value = "Get info about the current user preferences from CSD")
	@RequestMapping(method = GET, value = "/preferences")
	public UserPreferences getPreferences(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
		return preferencesService.getPreferences(authenticatedPerson.getUser().getPersonalCode());
	}
}