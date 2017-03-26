package ee.tuleva.onboarding.user;

import com.fasterxml.jackson.annotation.JsonView;
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
	public User user(@ApiIgnore @AuthenticationPrincipal User user) {
		return user;
	}

	@ApiOperation(value = "Get info about the current user preferences from CSD")
	@RequestMapping(method = GET, value = "/preferences")
	public UserPreferences getPreferences(@ApiIgnore @AuthenticationPrincipal User user) {
		return preferencesService.getPreferences(user.getPersonalCode());
	}
}