package ee.tuleva.onboarding.banking.statement;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.iso20022.camt053.DateAndDateTimeChoice;
import java.time.Instant;
import java.time.ZoneId;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.Test;

class BankStatementEntryTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Test
  void extractReceivedBefore_hasMicrosecondPrecision() throws Exception {
    var bookingDate = new DateAndDateTimeChoice();
    bookingDate.setDt(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-01-31"));

    var result = BankStatementEntry.extractReceivedBefore(bookingDate, TALLINN);

    assertThat(result).isEqualTo(Instant.parse("2026-01-31T21:59:59.999999Z"));
  }

  @Test
  void extractReceivedBefore_returnsNullForNullBookingDate() {
    var result = BankStatementEntry.extractReceivedBefore(null, TALLINN);

    assertThat(result).isNull();
  }
}
