package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionCommandTest {

  @Test
  void onCreate_setsCreatedAtWhenNull() {
    var command =
        TransactionCommand.builder()
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .build();

    command.onCreate();

    assertThat(command.getCreatedAt()).isNotNull();
  }

  @Test
  void onCreate_preservesExistingCreatedAt() {
    var existingTime = Instant.parse("2026-01-10T12:00:00Z");
    var command =
        TransactionCommand.builder()
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .createdAt(existingTime)
            .build();

    command.onCreate();

    assertThat(command.getCreatedAt()).isEqualTo(existingTime);
  }

  @Test
  void defaultStatus_isPending() {
    var command =
        TransactionCommand.builder()
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .build();

    assertThat(command.getStatus()).isEqualTo(CommandStatus.PENDING);
  }
}
