package ee.tuleva.onboarding.user;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
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

	@JsonView(UserView.Public.class)
	@ApiOperation(value = "Get info about the current user")
	@RequestMapping(method = GET, value = "/me")
	public User user(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
		return authenticatedPerson.getUser().orElseThrow(RuntimeException::new);
	}

	@ApiOperation(value = "Get info about the current user preferences from CSD")
	@RequestMapping(method = GET, value = "/preferences")
	public UserPreferences getPreferences(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
		return preferencesService.getPreferences(authenticatedPerson.getUser().orElseThrow(RuntimeException::new).getPersonalCode());
	}
}