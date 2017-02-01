package ee.tuleva.onboarding.user;

import io.swagger.annotations.ApiOperation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
public class UserController {

	@ApiOperation(value = "Get info about the current user")
	@RequestMapping(method = GET, value = "/me")
	public User user(@AuthenticationPrincipal User user) {
		return user;
	}

}