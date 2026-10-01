package ee.tuleva.onboarding.ledger;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalEntryPartTest {

  @Test
  void endOfDayTallinnIsTheSameDateInUtc() {
    for (var date : List.of("2026-01-31", "2026-07-31", "2026-03-29", "2026-10-25")) {
      var part = new JournalEntryPart("FIN:1:" + date, "FIN", LocalDate.parse(date), List.of());

      Instant transactionDate = part.transactionDate();

      assertThat(transactionDate.atZone(ZoneId.of("Europe/Tallinn")).toLocalDate())
          .isEqualTo(part.date());
      assertThat(transactionDate.atZone(UTC).toLocalDate()).isEqualTo(part.date());
      assertThat(transactionDate.plusNanos(1000).atZone(ZoneId.of("Europe/Tallinn")).toLocalDate())
          .isEqualTo(part.date().plusDays(1));
    }
  }
}
