package ee.tuleva.onboarding.savings.fund.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration;
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
  @Mock private SavingsFundConfiguration configuration;
  @InjectMocks private SavingsFundNavProvider navProvider;

  @Test
  void getCurrentNav_returnsNavFromRepository() {
    String isin = "EE0000003283";
    BigDecimal expectedNav = new BigDecimal("1.2345");
    when(configuration.getIsin()).thenReturn(isin);
    when(fundValueRepository.findLastValueForFund(isin))
        .thenReturn(
            Optional.of(
                new FundValue(isin, LocalDate.now(), expectedNav, "MANUAL", Instant.now())));

    BigDecimal result = navProvider.getCurrentNav();

    assertThat(result).isEqualTo(expectedNav);
  }

  @Test
  void getCurrentNav_throwsWhenNavNotFound() {
    String isin = "EE0000003283";
    when(configuration.getIsin()).thenReturn(isin);
    when(fundValueRepository.findLastValueForFund(isin)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> navProvider.getCurrentNav()).isInstanceOf(IllegalStateException.class);
  }
}
