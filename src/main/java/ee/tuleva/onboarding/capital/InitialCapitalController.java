package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
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
public class InitialCapitalController {

    public static final String INITIAL_CAPITAL_URI = "/me/initial-capital";
    private final InitialCapitalRepository initialCapitalRepository;

    @ApiOperation(value = "Get info about current user initial capital")
    @RequestMapping(method = GET, value = INITIAL_CAPITAL_URI)
    public InitialCapitalDto initialCapital(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
        InitialCapital initialCapital = initialCapitalRepository.findByUserId(authenticatedPerson.getUserId());
        return InitialCapitalDto.from(initialCapital);
    }

}