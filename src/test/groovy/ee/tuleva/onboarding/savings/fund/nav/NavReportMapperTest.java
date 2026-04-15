package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavReportMapperTest {

  @Mock private FundPositionRepository fundPositionRepository;

  @InjectMocks private NavReportMapper navReportMapper;

  @Test
  void mapsSavingsFundWithCustodyFee() {
    var navDate = LocalDate.of(2026, 3, 13);

    var result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 3, 16))
            .positionReportDate(navDate)
            .priceDate(navDate)
            .calculatedAt(Instant.parse("2026-03-16T13:20:00Z"))
            .securitiesDetail(
                List.of(
                    new SecurityDetail(
                        "IE00BMDBMY19",
                        "EMXC",
                        new BigDecimal("15543.00"),
                        new BigDecimal("41.8050"),
                        new BigDecimal("649775.12"),
                        navDate),
                    new SecurityDetail(
                        "IE00BFG1TM61",
                        "DWSF",
                        new BigDecimal("38755.69"),
                        new BigDecimal("33.6226"),
                        new BigDecimal("1303067.06"),
                        navDate)))
            .cashPosition(new BigDecimal("370794.18"))
            .receivables(ZERO)
            .payables(new BigDecimal("158773.00"))
            .pendingSubscriptions(ZERO)
            .pendingRedemptions(ZERO)
            .managementFeeAccrual(new BigDecimal("369.07"))
            .depotFeeAccrual(ZERO)
            .blackrockAdjustment(ZERO)
            .unitsOutstanding(new BigDecimal("7050814.517"))
            .navPerUnit(new BigDecimal("0.9792"))
            .aum(new BigDecimal("6903990.38"))
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            navDate, TKF100, SECURITY, "IE00BMDBMY19"))
        .thenReturn(
            Optional.of(
                FundPosition.builder()
                    .accountName("Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc")
                    .build()));
    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            navDate, TKF100, SECURITY, "IE00BFG1TM61"))
        .thenReturn(
            Optional.of(
                FundPosition.builder()
                    .accountName("iShares Developed World Screened Index Fund")
                    .build()));

    var rows = navReportMapper.map(result);

    assertThat(rows).hasSize(11);

    assertThat(rows.get(0).getAccountType()).isEqualTo("SECURITY");
    assertThat(rows.get(0).getAccountName())
        .isEqualTo("Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc");
    assertThat(rows.get(0).getAccountId()).isEqualTo("IE00BMDBMY19");
    assertThat(rows.get(0).getQuantity()).isEqualTo(new BigDecimal("15543.000"));
    assertThat(rows.get(0).getMarketPrice()).isEqualByComparingTo("41.8050");
    assertThat(rows.get(0).getMarketValue()).isEqualByComparingTo("649775.12");

    assertThat(rows.get(1).getAccountType()).isEqualTo("SECURITY");
    assertThat(rows.get(1).getAccountName())
        .isEqualTo("iShares Developed World Screened Index Fund");
    assertThat(rows.get(1).getAccountId()).isEqualTo("IE00BFG1TM61");

    assertThat(rows.get(2).getAccountType()).isEqualTo("CASH");
    assertThat(rows.get(2).getAccountName()).isEqualTo("Cash account in SEB Pank");
    assertThat(rows.get(2).getAccountId()).isEqualTo("EE0000003283");
    assertThat(rows.get(2).getQuantity()).isEqualByComparingTo("370794.18");
    assertThat(rows.get(2).getMarketPrice()).isEqualByComparingTo("1.00");
    assertThat(rows.get(2).getMarketValue()).isEqualByComparingTo("370794.18");

    assertThat(rows.get(3).getAccountType()).isEqualTo("RECEIVABLES");
    assertThat(rows.get(3).getAccountName())
        .isEqualTo("Total receivables of unsettled transactions");
    assertThat(rows.get(3).getQuantity()).isEqualByComparingTo("0");

    assertThat(rows.get(4).getAccountType()).isEqualTo("LIABILITY");
    assertThat(rows.get(4).getAccountName()).isEqualTo("Total payables of unsettled transactions");
    assertThat(rows.get(4).getQuantity()).isEqualByComparingTo("-158773.00");
    assertThat(rows.get(4).getMarketValue()).isEqualByComparingTo("-158773.00");

    assertThat(rows.get(5).getAccountType()).isEqualTo("RECEIVABLES");
    assertThat(rows.get(5).getAccountName()).isEqualTo("Receivables of outstanding units");

    assertThat(rows.get(6).getAccountType()).isEqualTo("LIABILITY");
    assertThat(rows.get(6).getAccountName()).isEqualTo("Payables of redeemed units");

    assertThat(rows.get(7).getAccountType()).isEqualTo("LIABILITY_FEE");
    assertThat(rows.get(7).getAccountName()).isEqualTo("Management fee");
    assertThat(rows.get(7).getQuantity()).isEqualByComparingTo("-369.07");
    assertThat(rows.get(7).getMarketValue()).isEqualByComparingTo("-369.07");

    assertThat(rows.get(8).getAccountType()).isEqualTo("LIABILITY_FEE");
    assertThat(rows.get(8).getAccountName()).isEqualTo("Custody fee");
    assertThat(rows.get(8).getQuantity()).isEqualByComparingTo("0");

    assertThat(rows.get(9).getAccountType()).isEqualTo("UNITS");
    assertThat(rows.get(9).getAccountName()).isEqualTo("Total outstanding units:");
    assertThat(rows.get(9).getAccountId()).isNull();
    assertThat(rows.get(9).getQuantity()).isEqualTo(new BigDecimal("7050814.517"));
    assertThat(rows.get(9).getMarketPrice()).isEqualByComparingTo("0.9792");
    assertThat(rows.get(9).getMarketValue()).isEqualByComparingTo("6903990.38");

    assertThat(rows.get(10).getAccountType()).isEqualTo("NAV");
    assertThat(rows.get(10).getAccountName()).isEqualTo("Net Asset Value");
    assertThat(rows.get(10).getAccountId()).isNull();
    assertThat(rows.get(10).getQuantity()).isEqualByComparingTo("1.00");
    assertThat(rows.get(10).getMarketPrice()).isEqualByComparingTo("0.9792");
    assertThat(rows.get(10).getMarketValue()).isEqualByComparingTo("0.9792");

    rows.forEach(
        row -> {
          assertThat(row.getNavDate()).isEqualTo(navDate);
          assertThat(row.getFundCode()).isEqualTo("TKF100");
          assertThat(row.getCurrency()).isEqualTo(EUR);
        });
  }

  @Test
  void mapsPillarFundWithOtherReceivables() {
    var navDate = LocalDate.of(2026, 3, 13);

    var result =
        NavCalculationResult.builder()
            .fund(TUK75)
            .calculationDate(LocalDate.of(2026, 3, 16))
            .positionReportDate(navDate)
            .priceDate(navDate)
            .calculatedAt(Instant.parse("2026-03-16T09:00:00Z"))
            .securitiesDetail(
                List.of(
                    new SecurityDetail(
                        "IE00BFG1TM61",
                        "DWSF",
                        new BigDecimal("8042414.77"),
                        new BigDecimal("33.6651"),
                        new BigDecimal("270748858.32"),
                        navDate)))
            .cashPosition(new BigDecimal("590345.14"))
            .receivables(ZERO)
            .payables(ZERO)
            .pendingSubscriptions(new BigDecimal("6980697.88"))
            .pendingRedemptions(ZERO)
            .managementFeeAccrual(new BigDecimal("68851.69"))
            .depotFeeAccrual(ZERO)
            .blackrockAdjustment(ZERO)
            .unitsOutstanding(new BigDecimal("672254297.303"))
            .navPerUnit(new BigDecimal("1.39535"))
            .aum(new BigDecimal("938028619.69"))
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            navDate, TUK75, SECURITY, "IE00BFG1TM61"))
        .thenReturn(
            Optional.of(
                FundPosition.builder()
                    .accountName("iShares Developed World Screened Index Fund")
                    .build()));

    var rows = navReportMapper.map(result);

    assertThat(rows).hasSize(11);

    assertThat(rows.get(0).getAccountType()).isEqualTo("SECURITY");

    assertThat(rows.get(1).getAccountType()).isEqualTo("CASH");
    assertThat(rows.get(1).getAccountId()).isEqualTo("EE3600109435");

    assertThat(rows.get(2).getAccountType()).isEqualTo("RECEIVABLES");
    assertThat(rows.get(2).getAccountName())
        .isEqualTo("Total receivables of unsettled transactions");

    assertThat(rows.get(3).getAccountType()).isEqualTo("LIABILITY");
    assertThat(rows.get(3).getAccountName()).isEqualTo("Total payables of unsettled transactions");

    assertThat(rows.get(4).getAccountType()).isEqualTo("RECEIVABLES");
    assertThat(rows.get(4).getAccountName()).isEqualTo("Receivables of outstanding units");
    assertThat(rows.get(4).getQuantity()).isEqualByComparingTo("6980697.88");

    assertThat(rows.get(5).getAccountType()).isEqualTo("RECEIVABLES");
    assertThat(rows.get(5).getAccountName()).isEqualTo("Other receivables");
    assertThat(rows.get(5).getQuantity()).isEqualTo(new BigDecimal("0.00"));

    assertThat(rows.get(6).getAccountType()).isEqualTo("LIABILITY");
    assertThat(rows.get(6).getAccountName()).isEqualTo("Payables of redeemed units");

    assertThat(rows.get(7).getAccountType()).isEqualTo("LIABILITY_FEE");
    assertThat(rows.get(7).getAccountName()).isEqualTo("Management fee");
    assertThat(rows.get(7).getQuantity()).isEqualByComparingTo("-68851.69");

    assertThat(rows.get(8).getAccountType()).isEqualTo("LIABILITY_FEE");
    assertThat(rows.get(8).getAccountName()).isEqualTo("Custody fee");
    assertThat(rows.get(8).getQuantity()).isEqualByComparingTo("0");

    assertThat(rows.get(9).getAccountType()).isEqualTo("UNITS");
    assertThat(rows.get(10).getAccountType()).isEqualTo("NAV");
    assertThat(rows.get(10).getFundCode()).isEqualTo("TUK75");
  }

  @Test
  void everyRowNavDateMatchesWhatGuardsQueryViaExpectedPositionReportDate() {
    // Coupling contract: mapper writer and guard reader must agree on nav_date via
    // NavCalculationService.expectedPositionReportDate. Protects against future drift.
    LocalDate calculationDate = LocalDate.of(2026, 4, 15);
    LocalDate positionReportDate = LocalDate.of(2026, 4, 14);

    PublicHolidays publicHolidays = org.mockito.Mockito.mock(PublicHolidays.class);
    when(publicHolidays.previousWorkingDay(calculationDate)).thenReturn(positionReportDate);

    LocalDate expectedNavDate =
        NavCalculationService.expectedPositionReportDate(TKF100, calculationDate, publicHolidays);

    NavCalculationResult result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(calculationDate)
            .positionReportDate(positionReportDate)
            .priceDate(positionReportDate)
            .calculatedAt(Instant.parse("2026-04-15T12:20:00Z"))
            .securitiesDetail(List.of())
            .cashPosition(new BigDecimal("100000.00"))
            .receivables(ZERO)
            .payables(ZERO)
            .pendingSubscriptions(ZERO)
            .pendingRedemptions(ZERO)
            .managementFeeAccrual(ZERO)
            .depotFeeAccrual(ZERO)
            .blackrockAdjustment(ZERO)
            .unitsOutstanding(new BigDecimal("100000.00000"))
            .navPerUnit(new BigDecimal("1.0000"))
            .aum(new BigDecimal("100000.00"))
            .build();

    List<NavReportRow> rows = navReportMapper.map(result);

    assertThat(rows).isNotEmpty();
    assertThat(rows).allSatisfy(row -> assertThat(row.getNavDate()).isEqualTo(expectedNavDate));
  }
}
