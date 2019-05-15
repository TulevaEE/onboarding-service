package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CapitalController {

    public static final String CAPITAL_URI = "/me/capital";
    private final CapitalService capitalService;
    private final UserService userService;

    @ApiOperation(value = "Get info about current user initial capital")
    @RequestMapping(method = GET, value = CAPITAL_URI)
    public CapitalStatement initialCapital(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
        Long userId = authenticatedPerson.getUserId();
        User user = userService.getById(userId);
        return user.getMember().map(member ->
            capitalService.getCapitalStatement(member.getId())).orElseThrow(() -> new RuntimeException());
    }

}