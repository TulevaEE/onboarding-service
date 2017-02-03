package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MandateController {

    private final MandateService mandateService;

    @ApiOperation(value = "Create a mandate")
    @RequestMapping(method = POST, value = "/mandate")
    public Mandate create(@AuthenticationPrincipal User user, CreateMandateCommand createMandateCommand) {
        return mandateService.save(user, createMandateCommand);
    }

    @ApiOperation(value = "Sign mandate")
    @RequestMapping(method = POST, value = "/mandate/{id}/signature")
    public void sign(@AuthenticationPrincipal User user) {

    }

}
