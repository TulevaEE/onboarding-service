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

    private final KPRClient xRoadClient;

    @ApiOperation(value = "Returns user last year average salary reverse calculated from 2nd pillar transactions")
    @RequestMapping(method = GET, value = "/average-salary")
    public Money getMyAverageSalay(@ApiIgnore @AuthenticationPrincipal User user) {
        /*
        PensionAccountTransactionType request = new PensionAccountTransactionType();

        request.setDateFrom(toXMLGregorianCalendar(LocalDate.now().minusYears(1)));
        request.setDateTo(toXMLGregorianCalendar(LocalDate.now()));

        PensionAccountTransactionResponseType response = xRoadClient.pensionAccountTransaction(request, user.getPersonalCode());

        return AverageIncomeCalculator.calculate(response.getMoney().getTransaction());
        */

        return Money.builder()
                .amount(new BigDecimal("2016"))
                .currency("EUR")
                .build();
    }


    XMLGregorianCalendar toXMLGregorianCalendar(LocalDate localDate) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendarDate(
                    localDate.getYear(),
                    localDate.getMonthValue(),
                    localDate.getDayOfMonth(),
                    DatatypeConstants.FIELD_UNDEFINED);
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

}
