package ee.tuleva.onboarding.banking.converter;

import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ZonedDateTimeToXmlGregorianCalendarConverter
    implements Converter<ZonedDateTime, XMLGregorianCalendar> {

  @Override
  @NonNull
  public XMLGregorianCalendar convert(ZonedDateTime zonedDateTime) {
    try {
      GregorianCalendar calendar = GregorianCalendar.from(zonedDateTime);
      return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
    } catch (DatatypeConfigurationException e) {
      throw new RuntimeException(e);
    }
  }
}
