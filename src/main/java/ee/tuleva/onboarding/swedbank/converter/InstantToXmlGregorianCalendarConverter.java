package ee.tuleva.onboarding.swedbank.converter;

import java.time.Instant;
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
public class InstantToXmlGregorianCalendarConverter
    implements Converter<Instant, XMLGregorianCalendar> {

  @Override
  @NonNull
  public XMLGregorianCalendar convert(Instant instant) {
    try {
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.setTimeInMillis(instant.toEpochMilli());
      return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
    } catch (DatatypeConfigurationException e) {
      throw new RuntimeException(e);
    }
  }
}
