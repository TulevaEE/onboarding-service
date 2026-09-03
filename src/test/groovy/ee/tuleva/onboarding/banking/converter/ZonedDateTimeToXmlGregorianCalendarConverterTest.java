package ee.tuleva.onboarding.banking.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Test;

class ZonedDateTimeToXmlGregorianCalendarConverterTest {

  private final ZonedDateTimeToXmlGregorianCalendarConverter converter =
      new ZonedDateTimeToXmlGregorianCalendarConverter();

  @Test
  void convertsUtcZonedDateTimeWithZeroNanoseconds() {
    ZonedDateTime input = ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 0, ZoneId.of("UTC"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-01-15T14:30:45.000Z");
    assertThat(result.getYear()).isEqualTo(2024);
    assertThat(result.getMonth()).isEqualTo(1);
    assertThat(result.getDay()).isEqualTo(15);
    assertThat(result.getHour()).isEqualTo(14);
    assertThat(result.getMinute()).isEqualTo(30);
    assertThat(result.getSecond()).isEqualTo(45);
    assertThat(result.getMillisecond()).isEqualTo(0);
    assertThat(result.getTimezone()).isEqualTo(0);
  }

  @Test
  void convertsTallinnZonedDateTimeDuringSummerDaylightSavingOffset() {
    ZonedDateTime input = ZonedDateTime.of(2024, 7, 15, 14, 30, 45, 0, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-07-15T14:30:45.000+03:00");
    assertThat(result.getTimezone()).isEqualTo(180);
  }

  @Test
  void convertsTallinnZonedDateTimeDuringWinterStandardOffset() {
    ZonedDateTime input = ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 0, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-01-15T14:30:45.000+02:00");
    assertThat(result.getTimezone()).isEqualTo(120);
  }

  @Test
  void convertsTallinnZonedDateTimeJustAfterSpringForwardTransition() {
    ZonedDateTime input = ZonedDateTime.of(2024, 3, 31, 4, 30, 0, 0, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-03-31T04:30:00.000+03:00");
    assertThat(result.getTimezone()).isEqualTo(180);
    assertThat(result.toGregorianCalendar().toInstant()).isEqualTo(input.toInstant());
  }

  @Test
  void convertsNegativeUtcOffsetZone() {
    ZonedDateTime input =
        ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 0, ZoneId.of("America/New_York"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-01-15T14:30:45.000-05:00");
    assertThat(result.getTimezone()).isEqualTo(-300);
  }

  @Test
  void appendsZeroFractionalSecondsEvenWhenInputHasNoFractionalPart() {
    ZonedDateTime input = ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 0, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).endsWith(".000+02:00");
    assertThat(result.getFractionalSecond()).isNotNull();
    assertThat(result.getFractionalSecond().doubleValue()).isEqualTo(0.0);
    assertThat(result.getMillisecond()).isEqualTo(0);
  }

  @Test
  void preservesMillisecondPrecisionFromNanoseconds() {
    ZonedDateTime input =
        ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 500_000_000, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-01-15T14:30:45.500+02:00");
    assertThat(result.getMillisecond()).isEqualTo(500);
    assertThat(result.toGregorianCalendar().toInstant()).isEqualTo(input.toInstant());
  }

  @Test
  void preservesSingleMillisecondPrecisionWithLeadingZeroPadding() {
    ZonedDateTime input =
        ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 1_000_000, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-01-15T14:30:45.001+02:00");
    assertThat(result.getMillisecond()).isEqualTo(1);
  }

  @Test
  void truncatesSubMillisecondNanosecondPrecisionInsteadOfRounding() {
    ZonedDateTime input =
        ZonedDateTime.of(2024, 1, 15, 14, 30, 45, 123_456_789, ZoneId.of("Europe/Tallinn"));

    XMLGregorianCalendar result = converter.convert(input);

    assertThat(result.toXMLFormat()).isEqualTo("2024-01-15T14:30:45.123+02:00");
    assertThat(result.getMillisecond()).isEqualTo(123);
    Instant roundTripInstant = result.toGregorianCalendar().toInstant();
    assertThat(roundTripInstant).isNotEqualTo(input.toInstant());
    assertThat(roundTripInstant).isEqualTo(Instant.parse("2024-01-15T12:30:45.123Z"));
  }

  @Test
  void throwsNullPointerExceptionForNullInput() {
    assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
  }
}
