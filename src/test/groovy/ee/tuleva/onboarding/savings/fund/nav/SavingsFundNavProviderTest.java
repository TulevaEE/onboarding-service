package ee.tuleva.onboarding.savings.fund.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundNavProviderTest {

  @Mock private FundValueRepository fundValueRepository;

  @InjectMocks private SavingsFundNavProvider provider;

  @Test
  void getCurrentNav_returnsNavFromIndexValues() {
    var fundValue =
        new FundValue(
            "EE0000003283",
            LocalDate.of(2025, 1, 15),
            new BigDecimal("9.69941"),
            "TULEVA",
            Instant.now());
    when(fundValueRepository.findLastValueForFund("EE0000003283"))
        .thenReturn(Optional.of(fundValue));

    BigDecimal result = provider.getCurrentNav();

    assertThat(result).isEqualByComparingTo("9.69941");
  }

  @Test
  void getCurrentNav_returnsOneWhenNoNavFound() {
    when(fundValueRepository.findLastValueForFund("EE0000003283")).thenReturn(Optional.empty());

    BigDecimal result = provider.getCurrentNav();

    assertThat(result).isEqualByComparingTo("1");
  }
}
