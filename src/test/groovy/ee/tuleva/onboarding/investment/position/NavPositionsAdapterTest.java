package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.savings.fund.nav.NavPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavPositionsAdapterTest {

  @Mock private FundPositionRepository fundPositionRepository;

  private NavPositionsAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new NavPositionsAdapter(fundPositionRepository);
  }

  @Test
  void findLatestNavDateByFundAndAsOfDate_delegates() {
    LocalDate asOfDate = LocalDate.of(2025, 1, 15);
    LocalDate latestDate = LocalDate.of(2025, 1, 14);
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, asOfDate))
        .willReturn(Optional.of(latestDate));

    Optional<LocalDate> result = adapter.findLatestNavDateByFundAndAsOfDate(TKF100, asOfDate);

    assertThat(result).contains(latestDate);
  }

  @Test
  void findLiabilityPositions_mapsFieldByField() {
    LocalDate navDate = LocalDate.of(2025, 1, 15);
    FundPosition position =
        FundPosition.builder()
            .navDate(navDate)
            .fund(TKF100)
            .accountType(AccountType.LIABILITY)
            .accountName("Payables of redeemed units")
            .marketValue(new BigDecimal("-10500.00"))
            .build();
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                navDate, TKF100, AccountType.LIABILITY))
        .willReturn(List.of(position));

    List<NavPosition> result = adapter.findLiabilityPositions(navDate, TKF100);

    assertThat(result)
        .containsExactly(
            new NavPosition("Payables of redeemed units", new BigDecimal("-10500.00")));
  }

  @Test
  void findReceivablePositions_mapsFieldByField() {
    LocalDate navDate = LocalDate.of(2025, 1, 15);
    FundPosition position =
        FundPosition.builder()
            .navDate(navDate)
            .fund(TKF100)
            .accountType(AccountType.RECEIVABLES)
            .accountName("Receivables of outstanding units")
            .marketValue(new BigDecimal("25000.00"))
            .build();
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                navDate, TKF100, AccountType.RECEIVABLES))
        .willReturn(List.of(position));

    List<NavPosition> result = adapter.findReceivablePositions(navDate, TKF100);

    assertThat(result)
        .containsExactly(
            new NavPosition("Receivables of outstanding units", new BigDecimal("25000.00")));
  }

  @Test
  void findLiabilityPositions_mapsNullMarketValue() {
    LocalDate navDate = LocalDate.of(2025, 1, 15);
    FundPosition position =
        FundPosition.builder()
            .navDate(navDate)
            .fund(TKF100)
            .accountType(AccountType.LIABILITY)
            .accountName("Some other payable")
            .marketValue(null)
            .build();
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                navDate, TKF100, AccountType.LIABILITY))
        .willReturn(List.of(position));

    List<NavPosition> result = adapter.findLiabilityPositions(navDate, TKF100);

    assertThat(result).containsExactly(new NavPosition("Some other payable", null));
  }
}
