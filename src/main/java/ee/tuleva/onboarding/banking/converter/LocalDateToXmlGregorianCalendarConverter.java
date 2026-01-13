package ee.tuleva.onboarding.banking.converter;

import java.time.LocalDate;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LocalDateToXmlGregorianCalendarConverter
    implements Converter<LocalDate, XMLGregorianCalendar> {

  @Override
  @NonNull
  @SneakyThrows
  public XMLGregorianCalendar convert(LocalDate localDate) {
    XMLGregorianCalendar calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar();
    calendar.setYear(localDate.getYear());
    calendar.setMonth(localDate.getMonthValue());
    calendar.setDay(localDate.getDayOfMonth());
    return calendar;
  }
}
