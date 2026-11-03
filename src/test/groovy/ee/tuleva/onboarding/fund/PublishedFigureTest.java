package ee.tuleva.onboarding.fund;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PublishedFigureTest {

  @Test
  void aFigureRoundedAsTheKidPrintsItGivesThePercentWithTheKidsDecimals() {
    assertThat(new PublishedFigure(new BigDecimal("0.005"), 2).percent())
        .isEqualTo(new BigDecimal("0.50"));
  }

  @Test
  void anUnroundedFigureIsRoundedHalfUpToTheKidsDecimals() {
    assertThat(new PublishedFigure(new BigDecimal("0.00459700"), 2).percent())
        .isEqualTo(new BigDecimal("0.46"));
    assertThat(new PublishedFigure(new BigDecimal("0.00445"), 2).percent())
        .isEqualTo(new BigDecimal("0.45"));
  }
}
