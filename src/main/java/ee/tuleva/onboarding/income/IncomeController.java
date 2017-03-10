package ee.tuleva.onboarding.income;

import ee.eesti.xtee6.kpr.PensionAccountTransactionResponseType;
import ee.eesti.xtee6.kpr.PensionAccountTransactionType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.kpr.KPRClient;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class IncomeController {

    private final AverageSalaryService averageSalaryService;

    @ApiOperation(value = "Returns user last year average salary reverse calculated from 2nd pillar transactions")
    @RequestMapping(method = GET, value = "/average-salary")
    public Money getMyAverageSalay(@ApiIgnore @AuthenticationPrincipal User user) {
        return averageSalaryService.getMyAverageSalary(user.getPersonalCode());
    }

}
