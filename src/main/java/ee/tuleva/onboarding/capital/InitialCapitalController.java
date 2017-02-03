package ee.tuleva.onboarding.capital;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.user.User;
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

    private final InitialCapitalRepository initialCapitalRepository;

    @ApiOperation(value = "Get info about current user initial capital")
    @RequestMapping(method = GET, value = "/initial-capital")
    @JsonView(InitialCapitalView.SkipUserField.class)
    public InitialCapital initialCapital(@ApiIgnore @AuthenticationPrincipal User user) {
        return initialCapitalRepository.findByUser(user);
    }

}