package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.stream.Collectors;

import static ee.tuleva.onboarding.mandate.application.ApplicationController.APPLICATIONS_URI;
import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Slf4j
@RestController
@RequestMapping("/v1" + APPLICATIONS_URI)
@RequiredArgsConstructor
public class ApplicationController {
    public static final String APPLICATIONS_URI = "/applications";

    private final ApplicationService applicationService;

    @ApiOperation(value = "Get applications")
    @RequestMapping(method = GET)
    public List<Application> get(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                 @RequestHeader(value = "Accept-Language") String language,
                                 @RequestParam("status") ApplicationStatus status) {
        return applicationService.get(authenticatedPerson, language).stream()
            .filter(application -> application.getStatus().equals(status))
            .collect(toList());
    }
}
