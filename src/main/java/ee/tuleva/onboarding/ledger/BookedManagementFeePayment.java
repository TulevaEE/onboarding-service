package ee.tuleva.onboarding.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record BookedManagementFeePayment(
    UUID externalReference,
    Instant transactionDate,
    BigDecimal amount,
    @Nullable String bankDescription) {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @JsonProperty
  public LocalDate bookingDate() {
    return transactionDate.atZone(ESTONIAN_ZONE).toLocalDate();
  }
}
