package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.savings.SavingsFundConfiguration;
import ee.tuleva.onboarding.savings.SavingsFundFees;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundFeesTest {

  @Mock private FundRepository fundRepository;

  private SavingsFundFees fees;

  @BeforeEach
  void setUp() {
    SavingsFundConfiguration configuration = new SavingsFundConfiguration();
    fees = new SavingsFundFees(fundRepository, configuration);
    given(fundRepository.findByIsin("EE0000003283"))
        .willReturn(Fund.builder().ongoingChargesFigure(new BigDecimal("0.0029")).build());
  }

  @Test
  void formatsThePercentWithADecimalCommaInEstonian() {
    assertThat(fees.ongoingChargesPercent(Locale.of("et"))).isEqualTo("0,29");
  }

  @Test
  void formatsThePercentWithADecimalPointInEnglish() {
    assertThat(fees.ongoingChargesPercent(Locale.ENGLISH)).isEqualTo("0.29");
  }
}
