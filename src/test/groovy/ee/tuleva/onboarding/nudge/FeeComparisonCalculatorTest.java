package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.conversion.ConversionHolding;
import ee.tuleva.onboarding.conversion.ConversionHoldings;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeComparisonCalculatorTest {

  private static final String TULEVA_SECOND_PILLAR_ISIN = "EE3600109435";

  @Mock private ConversionHoldings conversionHoldings;
  @Mock private FundRepository fundRepository;

  private final User user = sampleUser().build();
  private FeeComparisonCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new FeeComparisonCalculator(conversionHoldings, fundRepository);
  }

  @Test
  void comparesTheYearlyFeeOfTheCurrentSecondPillarHoldingsWithTulevasFund() {
    given(conversionHoldings.forPerson(user))
        .willReturn(
            List.of(
                holding(2, new BigDecimal("15000")),
                holding(2, new BigDecimal("5000")),
                holding(3, new BigDecimal("99999"))));
    given(fundRepository.findByIsin(TULEVA_SECOND_PILLAR_ISIN))
        .willReturn(Fund.builder().ongoingChargesFigure(new BigDecimal("0.0028")).build());

    var comparison = calculator.forSecondPillar(user, new BigDecimal("0.0065"));

    assertThat(comparison).contains(new FeeComparison(new BigDecimal("0.65"), 130, 56, 74));
  }

  @Test
  void noComparisonWhenTheFeeIsNotHigh() {
    assertThat(calculator.forSecondPillar(user, new BigDecimal("0.003"))).isEmpty();
    assertThat(calculator.forSecondPillar(user, null)).isEmpty();
  }

  @Test
  void noComparisonWithoutSecondPillarValue() {
    given(conversionHoldings.forPerson(user))
        .willReturn(List.of(holding(3, new BigDecimal("500"))));
    given(fundRepository.findByIsin(TULEVA_SECOND_PILLAR_ISIN))
        .willReturn(Fund.builder().ongoingChargesFigure(new BigDecimal("0.0028")).build());

    assertThat(calculator.forSecondPillar(user, new BigDecimal("0.0065"))).isEmpty();
  }

  @Test
  void noComparisonWhenTulevaWouldNotBeCheaper() {
    given(conversionHoldings.forPerson(user))
        .willReturn(List.of(holding(2, new BigDecimal("100"))));
    given(fundRepository.findByIsin(TULEVA_SECOND_PILLAR_ISIN))
        .willReturn(Fund.builder().ongoingChargesFigure(new BigDecimal("0.0040")).build());

    assertThat(calculator.forSecondPillar(user, new BigDecimal("0.0035"))).isEmpty();
  }

  private static ConversionHolding holding(int pillar, BigDecimal value) {
    return new ConversionHolding(
        pillar,
        "ISIN" + pillar,
        false,
        false,
        true,
        value,
        BigDecimal.ONE,
        new BigDecimal("0.0065"));
  }
}
