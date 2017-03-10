package ee.tuleva.onboarding.income;

import ee.eesti.xtee6.kpr.PensionAccountTransactionResponseType;
import ee.eesti.xtee6.kpr.PensionAccountTransactionType;
import ee.tuleva.onboarding.kpr.KPRClient;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;

@Service
@AllArgsConstructor
public class AverageSalaryService {

    @Autowired
    private KPRClient kprClient;

    public Money getMyAverageSalary(String userIdCode) {
        PensionAccountTransactionType request = new PensionAccountTransactionType();

        request.setDateFrom(toXMLGregorianCalendar(LocalDate.now().minusYears(1)));
        request.setDateTo(toXMLGregorianCalendar(LocalDate.now()));

        PensionAccountTransactionResponseType response = kprClient.pensionAccountTransaction(request, userIdCode);

        return AverageIncomeCalculator.calculate(response.getMoney().getTransaction());
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
