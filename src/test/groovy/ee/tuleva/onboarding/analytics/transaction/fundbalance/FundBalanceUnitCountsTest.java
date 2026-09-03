package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundBalanceUnitCountsTest {

  private static final LocalDate DATE = LocalDate.of(2026, 4, 24);

  @Mock FundBalanceRepository fundBalanceRepository;

  @InjectMocks FundBalanceUnitCounts fundUnitCounts;

  @Test
  void sumsCountUnitsAndCountUnitsFmFromLatestFundBalanceAsOfDate() {
    var fundBalance =
        FundBalance.builder()
            .isin("EE3600109435")
            .countUnits(new BigDecimal("9000000"))
            .countUnitsFm(new BigDecimal("123.45"))
            .build();
    given(
            fundBalanceRepository.findFirstByIsinAndRequestDateLessThanEqualOrderByRequestDateDesc(
                "EE3600109435", DATE))
        .willReturn(Optional.of(fundBalance));

    var result = fundUnitCounts.totalUnitsAsOf("EE3600109435", DATE);

    assertThat(result).contains(new BigDecimal("9000123.45"));
  }

  @Test
  void emptyWhenNoFundBalanceOnOrBeforeDate() {
    given(
            fundBalanceRepository.findFirstByIsinAndRequestDateLessThanEqualOrderByRequestDateDesc(
                "EE3600109435", DATE))
        .willReturn(Optional.empty());

    var result = fundUnitCounts.totalUnitsAsOf("EE3600109435", DATE);

    assertThat(result).isEmpty();
  }
}
