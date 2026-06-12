package ee.tuleva.onboarding.investment.epis;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class R45TransactionTypeTest {

  @ParameterizedTest
  @CsvSource({
    "SUB, INFLOW",
    "RED, OUTFLOW",
    "SWI, OUTFLOW",
    "SWS, INFLOW",
    "COR, OUTFLOW",
    "PAAV, OUTFLOW",
    "RES, INFLOW"
  })
  void mapsTransactionTypeToDirection(R45TransactionType type, Direction direction) {
    assertThat(type.getDirection()).isEqualTo(direction);
  }

  @Test
  void findIsCaseInsensitive() {
    assertThat(R45TransactionType.find("sub")).contains(R45TransactionType.SUB);
  }

  @Test
  void findReturnsEmptyForUnknownCode() {
    assertThat(R45TransactionType.find("XXX")).isEqualTo(Optional.empty());
  }
}
