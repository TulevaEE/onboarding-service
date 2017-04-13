package ee.tuleva.onboarding.income;

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
public class IncomeController {

    private final AverageSalaryService averageSalaryService;

    @ApiOperation(value = "Returns user last year average salary reverse calculated from 2nd pillar transactions")
    @RequestMapping(method = GET, value = "/average-salary")
    public Money getMyAverageSalay(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
        return averageSalaryService.getMyAverageSalary(authenticatedPerson.getUser().orElseThrow(RuntimeException::new).getPersonalCode());
    }

}
