package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnFundNavProviderTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 2, 10);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 2, 9);

  @Mock private FundNavQueryService fundNavQueryService;

  @InjectMocks private OwnFundNavProvider provider;

  @Test
  void findLatestNav_returnsANavInsideTheReasonableRange() {
    givenNav(new BigDecimal("1.2345"));

    assertThat(provider.findLatestNav(TUV100, AS_OF_DATE)).contains(new BigDecimal("1.2345"));
  }

  @Test
  void findLatestNav_returnsEmptyWhenNoNavIsRecorded() {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUV100.getCode(), AS_OF_DATE))
        .willReturn(Optional.empty());

    assertThat(provider.findLatestNav(TUV100, AS_OF_DATE)).isEmpty();
  }

  @Test
  void findLatestNav_ignoresANavAboveTheReasonableRange() {
    givenNav(new BigDecimal("10.01"));

    assertThat(provider.findLatestNav(TUV100, AS_OF_DATE)).isEmpty();
  }

  @Test
  void findLatestNav_ignoresANavBelowTheReasonableRange() {
    givenNav(new BigDecimal("0.009"));

    assertThat(provider.findLatestNav(TUV100, AS_OF_DATE)).isEmpty();
  }

  @Test
  void findLatestNav_keepsTheRangeBoundariesThemselves() {
    givenNav(new BigDecimal("0.01"));
    assertThat(provider.findLatestNav(TUV100, AS_OF_DATE)).contains(new BigDecimal("0.01"));

    givenNav(new BigDecimal("10.0"));
    assertThat(provider.findLatestNav(TUV100, AS_OF_DATE)).contains(new BigDecimal("10.0"));
  }

  @Test
  void latestNav_returnsANavInsideTheReasonableRange() {
    givenNav(new BigDecimal("1.2345"));

    assertThat(provider.latestNav(TUV100, AS_OF_DATE)).isEqualByComparingTo("1.2345");
  }

  @Test
  void latestNav_throwsOnANavOutsideTheReasonableRange() {
    givenNav(new BigDecimal("10.01"));

    assertThatThrownBy(() -> provider.latestNav(TUV100, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void latestNav_throwsWhenNoNavIsRecorded() {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUV100.getCode(), AS_OF_DATE))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> provider.latestNav(TUV100, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  private void givenNav(BigDecimal nav) {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUV100.getCode(), AS_OF_DATE))
        .willReturn(Optional.of(NAV_DATE));
    given(fundNavQueryService.findNavPerUnit(TUV100.getCode(), NAV_DATE))
        .willReturn(Optional.of(nav));
  }
}
