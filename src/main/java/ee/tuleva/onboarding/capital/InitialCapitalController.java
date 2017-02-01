package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
public class InitialCapitalController {

    InitialCapitalRepository initialCapitalRepository;

    @Autowired
    InitialCapitalController(InitialCapitalRepository initialCapitalRepository) {
        this.initialCapitalRepository = initialCapitalRepository;
    }

    @ApiOperation(value = "Get info about current user initial capital")
    @RequestMapping(method = GET, value = "/initial-capital")
    public InitialCapital initialCapital(@AuthenticationPrincipal User user) {
        return initialCapitalRepository.findByUser(user);
    }

}