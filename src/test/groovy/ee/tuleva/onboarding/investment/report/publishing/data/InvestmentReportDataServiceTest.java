package ee.tuleva.onboarding.investment.report.publishing.data;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportDataServiceTest {

  @Mock private NavReportViewRepository navReportRepository;
  @Mock private InstrumentReferenceRepository instrumentReferenceRepository;
  @Mock private PortfolioCostBasisService costBasisService;
  @InjectMocks private InvestmentReportDataService service;

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 3, 31);

  @Test
  void getReportDataReturnsContextWithSecuritiesAndCash() {
    var sec1 =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50.00"),
            new BigDecimal("5000"));
    var sec2 =
        navRow(
            "SECURITY",
            "Fund B",
            "IE00BFG1TM61",
            new BigDecimal("200"),
            new BigDecimal("30.00"),
            new BigDecimal("6000"));
    var cash = navRow("CASH", "SEB deposit", null, null, null, new BigDecimal("1000"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("12000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec1, sec2, cash, units));

    var ref1 = instrumentRef("IE0009FT4LX4", "CCF Developed World", "BlackRock", "IE");
    var ref2 = instrumentRef("IE00BFG1TM61", "BlackRock ISF DW Screened", "BlackRock", "IE");
    given(instrumentReferenceRepository.findByIsinIn(List.of("IE0009FT4LX4", "IE00BFG1TM61")))
        .willReturn(List.of(ref1, ref2));
    var cb1 =
        new PortfolioCostBasisSnapshot(
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("48.00"),
            new BigDecimal("4800"),
            NAV_DATE);
    var cb2 =
        new PortfolioCostBasisSnapshot(
            "IE00BFG1TM61",
            new BigDecimal("200"),
            new BigDecimal("28.00"),
            new BigDecimal("5600"),
            NAV_DATE);
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of(cb1, cb2));

    // No previous month data
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(null);

    var ctx = service.getReportData(TUK75, MARCH_2026);

    assertThat(ctx.fundTitle()).isEqualTo("Tuleva Maailma Aktsiate Pensionifond");
    assertThat(ctx.reportDate()).isEqualTo("31.03.2026");
    assertThat(ctx.fundNav()).isEqualByComparingTo(new BigDecimal("12000"));

    assertThat(ctx.securitiesSections()).hasSize(1);
    var section = ctx.securitiesSections().getFirst();
    assertThat(section.heading()).isEqualTo("Aktsiafondid");
    assertThat(section.rows()).hasSize(2);
    assertThat(section.rows().get(0).displayName()).isEqualTo("CCF Developed World");
    assertThat(section.rows().get(0).avgCostPerUnit())
        .isEqualByComparingTo(new BigDecimal("48.00"));
    assertThat(section.rows().get(0).avgCostTotal()).isEqualByComparingTo(new BigDecimal("4800"));
    assertThat(section.rows().get(1).displayName()).isEqualTo("BlackRock ISF DW Screened");
    assertThat(section.rows().get(1).avgCostPerUnit())
        .isEqualByComparingTo(new BigDecimal("28.00"));
    assertThat(section.rows().get(1).avgCostTotal()).isEqualByComparingTo(new BigDecimal("5600"));

    // 5000/12000 + 6000/12000 = 11000/12000
    assertThat(section.totalMarketValue()).isEqualByComparingTo(new BigDecimal("11000"));
    assertThat(section.totalCost()).isEqualByComparingTo(new BigDecimal("10400"));
    assertThat(section.totalChange()).isNull();

    assertThat(ctx.cashRows()).hasSize(1);
    assertThat(ctx.cashRows().getFirst().displayName()).isEqualTo("Arvelduskonto");
    assertThat(ctx.cashRows().getFirst().institution()).isEqualTo("AS SEB Pank");
  }

  @Test
  void getReportDataIncludesReceivablesInCashSection() {
    var sec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50.00"),
            new BigDecimal("5000"));
    var rec = navRow("RECEIVABLES", "Other receivables", null, null, null, new BigDecimal("500"));
    var settledRec =
        navRow(
            "RECEIVABLES",
            "Total receivables of unsettled transactions",
            null,
            null,
            null,
            new BigDecimal("200"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("5500"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec, rec, settledRec, units));
    given(instrumentReferenceRepository.findByIsinIn(List.of("IE0009FT4LX4")))
        .willReturn(List.of());
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of());
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(null);

    var ctx = service.getReportData(TUK75, MARCH_2026);

    // Other receivables included, settlement receivables excluded
    assertThat(ctx.cashRows()).hasSize(1);
    assertThat(ctx.cashRows().getFirst().displayName()).isEqualTo("Muud nõuded");
    assertThat(ctx.cashRows().getFirst().marketValueTotal())
        .isEqualByComparingTo(new BigDecimal("500"));
  }

  @Test
  void getReportDataFallsBackToAccountNameWhenNoInstrumentReference() {
    var sec =
        navRow(
            "SECURITY",
            "Unknown Fund XYZ",
            "XX1234567890",
            new BigDecimal("10"),
            new BigDecimal("100"),
            new BigDecimal("1000"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("1000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec, units));
    given(instrumentReferenceRepository.findByIsinIn(List.of("XX1234567890")))
        .willReturn(List.of());
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of());
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(null);

    var ctx = service.getReportData(TUK75, MARCH_2026);

    assertThat(ctx.securitiesSections().getFirst().rows().getFirst().displayName())
        .isEqualTo("Unknown Fund XYZ");
  }

  @Test
  void getReportDataComputesPreviousMonthChange() {
    // Current month
    var sec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("60"),
            new BigDecimal("6000"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("10000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec, units));
    given(instrumentReferenceRepository.findByIsinIn(List.of("IE0009FT4LX4")))
        .willReturn(List.of());
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of());

    // Previous month: security was 50% of NAV
    var prevDate = LocalDate.of(2026, 2, 28);
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(prevDate);
    var prevSec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50"),
            new BigDecimal("5000"));
    var prevUnits = navRow("UNITS", "Total", null, null, null, new BigDecimal("10000"));
    given(navReportRepository.findPublishedByNavDateAndFundCode(prevDate, "TUK75"))
        .willReturn(List.of(prevSec, prevUnits));

    var ctx = service.getReportData(TUK75, MARCH_2026);

    // Current: 6000/10000 = 0.6, Previous: 5000/10000 = 0.5, Change: +0.1
    assertThat(ctx.securitiesSections().getFirst().totalChange())
        .isEqualByComparingTo(new BigDecimal("0.1"));
  }

  @Test
  void getReportDataThrowsWhenNoPublishedNavData() {
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(null);

    assertThatThrownBy(() -> service.getReportData(TUK75, MARCH_2026))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No published NAV data");
  }

  @Test
  void getReportDataThrowsWhenNoUnitsRow() {
    var sec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50.00"),
            new BigDecimal("5000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec));

    assertThatThrownBy(() -> service.getReportData(TUK75, MARCH_2026))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getReportDataExcludesZeroReceivablesFromCashSection() {
    var sec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50.00"),
            new BigDecimal("5000"));
    var zeroRec = navRow("RECEIVABLES", "Other receivables", null, null, null, BigDecimal.ZERO);
    var cash = navRow("CASH", "SEB deposit", null, null, null, new BigDecimal("1000"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("6000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec, zeroRec, cash, units));
    given(instrumentReferenceRepository.findByIsinIn(List.of("IE0009FT4LX4")))
        .willReturn(List.of());
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of());
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(null);

    var ctx = service.getReportData(TUK75, MARCH_2026);

    // Only cash row, no "Muud nõuded" row since receivables are zero
    assertThat(ctx.cashRows()).hasSize(1);
    assertThat(ctx.cashRows().getFirst().displayName()).isEqualTo("Arvelduskonto");
  }

  @Test
  void getReportDataHandlesPreviousMonthWithEmptyRows() {
    var sec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("60"),
            new BigDecimal("6000"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("10000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec, units));
    given(instrumentReferenceRepository.findByIsinIn(List.of("IE0009FT4LX4")))
        .willReturn(List.of());
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of());

    // Previous month has a NAV date but empty rows
    var prevDate = LocalDate.of(2026, 2, 28);
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(prevDate);
    given(navReportRepository.findPublishedByNavDateAndFundCode(prevDate, "TUK75"))
        .willReturn(List.of());

    var ctx = service.getReportData(TUK75, MARCH_2026);

    // No previous month data available, so change should be null
    assertThat(ctx.securitiesSections().getFirst().totalChange()).isNull();
  }

  @Test
  void getReportDataHandlesPreviousMonthWithCashAndReceivables() {
    var sec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("60"),
            new BigDecimal("6000"));
    var cash = navRow("CASH", "SEB deposit", null, null, null, new BigDecimal("3000"));
    var rec = navRow("RECEIVABLES", "Other receivables", null, null, null, new BigDecimal("500"));
    var units = navRow("UNITS", "Total", null, null, null, new BigDecimal("10000"));

    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", MARCH_2026.atDay(1), MARCH_2026.atEndOfMonth()))
        .willReturn(NAV_DATE);
    given(navReportRepository.findPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(List.of(sec, cash, rec, units));
    given(instrumentReferenceRepository.findByIsinIn(List.of("IE0009FT4LX4")))
        .willReturn(List.of());
    given(costBasisService.snapshotForFundAndDate(TUK75, NAV_DATE)).willReturn(List.of());

    // Previous month with cash and receivables
    var prevDate = LocalDate.of(2026, 2, 28);
    given(
            navReportRepository.findLatestPublishedNavDate(
                "TUK75", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .willReturn(prevDate);
    var prevSec =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50"),
            new BigDecimal("5000"));
    var prevCash = navRow("CASH", "SEB deposit", null, null, null, new BigDecimal("4000"));
    var prevRec =
        navRow("RECEIVABLES", "Other receivables", null, null, null, new BigDecimal("200"));
    var prevUnits = navRow("UNITS", "Total", null, null, null, new BigDecimal("10000"));
    given(navReportRepository.findPublishedByNavDateAndFundCode(prevDate, "TUK75"))
        .willReturn(List.of(prevSec, prevCash, prevRec, prevUnits));

    var ctx = service.getReportData(TUK75, MARCH_2026);

    // Cash change = current cash+rec NAV% - previous cash+rec NAV%
    assertThat(ctx.cashTotalChange()).isNotNull();
  }

  @Test
  void formatCashAccountIdentifiesBanks() {
    assertThat(InvestmentReportDataService.formatCashAccount("SEB deposit account").name())
        .isEqualTo("Arvelduskonto");
    assertThat(InvestmentReportDataService.formatCashAccount("SEB deposit account").institution())
        .isEqualTo("AS SEB Pank");
    assertThat(InvestmentReportDataService.formatCashAccount("Swedbank current").institution())
        .isEqualTo("Swedbank AS");
    assertThat(InvestmentReportDataService.formatCashAccount("LHV savings").institution())
        .isEqualTo("AS LHV Pank");
    assertThat(InvestmentReportDataService.formatCashAccount("Unknown bank").name())
        .isEqualTo("Arvelduskonto");
    assertThat(InvestmentReportDataService.formatCashAccount("Luminor current").institution())
        .isEqualTo("Luminor Bank AS");
    assertThat(InvestmentReportDataService.formatCashAccount("Unknown bank").institution())
        .isEqualTo("Unknown bank");
    assertThat(InvestmentReportDataService.formatCashAccount(null).institution()).isNull();
  }

  private NavReportView navRow(
      String accountType,
      String accountName,
      String accountId,
      BigDecimal quantity,
      BigDecimal marketPrice,
      BigDecimal marketValue) {
    return NavReportViewFixture.create(
        NAV_DATE, "TUK75", accountType, accountName, accountId, quantity, marketPrice, marketValue);
  }

  private InstrumentReference instrumentRef(
      String isin, String displayName, String fundManager, String country) {
    return InstrumentReferenceFixture.create(isin, displayName, fundManager, country);
  }
}
