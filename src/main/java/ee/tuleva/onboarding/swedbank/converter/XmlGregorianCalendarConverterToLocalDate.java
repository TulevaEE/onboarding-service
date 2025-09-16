package ee.tuleva.onboarding.swedbank.converter;

import java.time.LocalDate;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class XmlGregorianCalendarConverterToLocalDate
    implements Converter<XMLGregorianCalendar, LocalDate> {

  @Override
  @NonNull
  @SneakyThrows
  public LocalDate convert(XMLGregorianCalendar xmlGregorianCalendar) {
    return LocalDate.of(
        xmlGregorianCalendar.getYear(),
        xmlGregorianCalendar.getMonth(),
        xmlGregorianCalendar.getDay());
  }
}
