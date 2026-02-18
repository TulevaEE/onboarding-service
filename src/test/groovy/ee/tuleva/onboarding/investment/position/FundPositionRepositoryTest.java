package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundPositionRepositoryTest {

  @Autowired private FundPositionRepository fundPositionRepository;

  @BeforeEach
  void setUp() {
    fundPositionRepository.deleteAll();
  }

  @Test
  void findMarketValueByFundAndAccountId_returnsValueForExactDate() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 15);
    BigDecimal marketValue = new BigDecimal("5000000.00");
    savePosition(TUV100, "IE00TEST1234", reportingDate, marketValue);

    var result =
        fundPositionRepository.findMarketValueByFundAndAccountId(
            TUV100, "IE00TEST1234", reportingDate);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo(marketValue);
  }

  @Test
  void findMarketValueByFundAndAccountId_returnsMostRecentValueBeforeDate() {
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    BigDecimal marketValue = new BigDecimal("5000000.00");
    savePosition(TUV100, "IE00TEST1234", friday, marketValue);

    var result =
        fundPositionRepository.findMarketValueByFundAndAccountId(TUV100, "IE00TEST1234", saturday);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo(marketValue);
  }

  @Test
  void findMarketValueByFundAndAccountId_returnsEmptyWhenNoData() {
    var result =
        fundPositionRepository.findMarketValueByFundAndAccountId(
            TUV100, "IE00TEST1234", LocalDate.of(2025, 1, 15));

    assertThat(result).isEmpty();
  }

  @Test
  void findMarketValueByFundAndAccountId_filtersByFund() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 15);
    savePosition(TUV100, "IE00TEST1234", reportingDate, new BigDecimal("5000000.00"));
    savePosition(TUK75, "IE00TEST1234", reportingDate, new BigDecimal("3000000.00"));

    var result =
        fundPositionRepository.findMarketValueByFundAndAccountId(
            TUV100, "IE00TEST1234", reportingDate);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo(new BigDecimal("5000000.00"));
  }

  @Test
  void sumMarketValueByFund_sumsAllHoldingsForLatestDate() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 15);
    savePosition(TUV100, "IE00TEST1234", reportingDate, new BigDecimal("5000000.00"));
    savePosition(TUV100, "IE00TEST5678", reportingDate, new BigDecimal("3000000.00"));

    BigDecimal result = fundPositionRepository.sumMarketValueByFund(TUV100, reportingDate);

    assertThat(result).isEqualByComparingTo(new BigDecimal("8000000.00"));
  }

  @Test
  void sumMarketValueByFund_usesLatestReportingDateBeforeAsOfDate() {
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    savePosition(TUV100, "IE00TEST1234", friday, new BigDecimal("5000000.00"));

    BigDecimal result = fundPositionRepository.sumMarketValueByFund(TUV100, saturday);

    assertThat(result).isEqualByComparingTo(new BigDecimal("5000000.00"));
  }

  @Test
  void sumMarketValueByFund_returnsZeroWhenNoData() {
    BigDecimal result =
        fundPositionRepository.sumMarketValueByFund(TUV100, LocalDate.of(2025, 1, 15));

    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void findLatestReportingDateByFundAndAsOfDate_returnsExactDate() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 15);
    savePosition(TUV100, "IE00TEST1234", reportingDate, new BigDecimal("5000000.00"));

    var result =
        fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, reportingDate);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(reportingDate);
  }

  @Test
  void findLatestReportingDateByFundAndAsOfDate_returnsLatestDateBeforeAsOfDate() {
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    savePosition(TUV100, "IE00TEST1234", friday, new BigDecimal("5000000.00"));

    var result = fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, saturday);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(friday);
  }

  @Test
  void findLatestReportingDateByFundAndAsOfDate_returnsEmptyWhenNoDataBeforeDate() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 15);
    savePosition(TUV100, "IE00TEST1234", reportingDate, new BigDecimal("5000000.00"));

    var result =
        fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(
            TUV100, LocalDate.of(2025, 1, 14));

    assertThat(result).isEmpty();
  }

  @Test
  void findLatestReportingDateByFundAndAsOfDate_filtersByFund() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 15);
    savePosition(TUK75, "IE00TEST1234", reportingDate, new BigDecimal("5000000.00"));

    var result =
        fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, reportingDate);

    assertThat(result).isEmpty();
  }

  private void savePosition(
      TulevaFund fund, String accountId, LocalDate reportingDate, BigDecimal marketValue) {
    FundPosition position =
        FundPosition.builder()
            .fund(fund)
            .accountId(accountId)
            .reportingDate(reportingDate)
            .marketValue(marketValue)
            .accountType(AccountType.SECURITY)
            .accountName("Position " + accountId)
            .quantity(new BigDecimal("1000"))
            .marketPrice(new BigDecimal("100.00"))
            .currency("EUR")
            .build();
    fundPositionRepository.save(position);
  }
}
