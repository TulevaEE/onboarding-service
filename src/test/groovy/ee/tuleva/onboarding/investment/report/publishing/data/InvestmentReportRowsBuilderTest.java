package ee.tuleva.onboarding.investment.report.publishing.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.instrument.InstrumentReferenceFixture;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportRowsBuilderTest {

  @Mock private InstrumentReferenceService instrumentReferenceService;
  @InjectMocks private InvestmentReportRowsBuilder builder;

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 3, 31);

  @Test
  void buildSecurityRowsThrowsWhenMarketValueMissing() {
    var sec = navRow("SECURITY", "Fund A", "IE0009FT4LX4", new BigDecimal("100"), null, null);

    assertThatThrownBy(
            () -> builder.buildSecurityRows(List.of(sec), Map.of(), new BigDecimal("12000")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no market value");
  }

  @Test
  void buildSecurityRowsFallsBackToAccountNameWhenNoInstrumentReference() {
    var sec =
        navRow(
            "SECURITY",
            "Unknown Fund XYZ",
            "XX1234567890",
            new BigDecimal("10"),
            new BigDecimal("100"),
            new BigDecimal("1000"));
    given(instrumentReferenceService.findByIsin("XX1234567890")).willReturn(Optional.empty());

    var rows = builder.buildSecurityRows(List.of(sec), Map.of(), new BigDecimal("1000"));

    assertThat(rows.getFirst().displayName()).isEqualTo("Unknown Fund XYZ");
  }

  @Test
  void buildSecurityRowsUsesInstrumentReferenceAndCostBasisWhenAvailable() {
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
    var ref1 =
        InstrumentReferenceFixture.create("IE0009FT4LX4", "CCF Developed World", "BlackRock", "IE");
    var ref2 =
        InstrumentReferenceFixture.create(
            "IE00BFG1TM61", "BlackRock ISF DW Screened", "BlackRock", "IE");
    given(instrumentReferenceService.findByIsin("IE0009FT4LX4")).willReturn(Optional.of(ref1));
    given(instrumentReferenceService.findByIsin("IE00BFG1TM61")).willReturn(Optional.of(ref2));
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
    var costBasisMap = Map.of("IE0009FT4LX4", cb1, "IE00BFG1TM61", cb2);

    var rows =
        builder.buildSecurityRows(List.of(sec1, sec2), costBasisMap, new BigDecimal("12000"));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).displayName()).isEqualTo("CCF Developed World");
    assertThat(rows.get(0).avgCostPerUnit()).isEqualByComparingTo(new BigDecimal("48.00"));
    assertThat(rows.get(0).avgCostTotal()).isEqualByComparingTo(new BigDecimal("4800"));
    assertThat(rows.get(1).displayName()).isEqualTo("BlackRock ISF DW Screened");
    assertThat(rows.get(1).avgCostPerUnit()).isEqualByComparingTo(new BigDecimal("28.00"));
    assertThat(rows.get(1).avgCostTotal()).isEqualByComparingTo(new BigDecimal("5600"));
  }

  @Test
  void securitiesTotalCostIfCompleteIsNullWhenAnyRowMissingCostBasis() {
    var withCost =
        navRow(
            "SECURITY",
            "Fund A",
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("50.00"),
            new BigDecimal("5000"));
    var withoutCost =
        navRow(
            "SECURITY",
            "Fund B",
            "IE00BFG1TM61",
            new BigDecimal("200"),
            new BigDecimal("30.00"),
            new BigDecimal("6000"));
    given(instrumentReferenceService.findByIsin("IE0009FT4LX4")).willReturn(Optional.empty());
    given(instrumentReferenceService.findByIsin("IE00BFG1TM61")).willReturn(Optional.empty());
    var cb =
        new PortfolioCostBasisSnapshot(
            "IE0009FT4LX4",
            new BigDecimal("100"),
            new BigDecimal("48.00"),
            new BigDecimal("4800"),
            NAV_DATE);
    var costBasisMap = Map.of("IE0009FT4LX4", cb);

    var rows =
        builder.buildSecurityRows(
            List.of(withCost, withoutCost), costBasisMap, new BigDecimal("12000"));
    var totalMarketValue =
        rows.stream().map(row -> row.marketValueTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(totalMarketValue).isEqualByComparingTo(new BigDecimal("11000"));
    assertThat(InvestmentReportRowsBuilder.securitiesTotalCostIfComplete(rows)).isNull();
  }

  @Test
  void buildCashRowsIncludesOtherReceivablesRowWhenPositive() {
    var rows = builder.buildCashRows(List.of(), new BigDecimal("500"), new BigDecimal("5500"));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().displayName()).isEqualTo("Muud nõuded");
    assertThat(rows.getFirst().marketValueTotal()).isEqualByComparingTo(new BigDecimal("500"));
  }

  @Test
  void buildCashRowsExcludesZeroReceivablesRow() {
    var cash = navRow("CASH", "SEB deposit", null, null, null, new BigDecimal("1000"));

    var rows = builder.buildCashRows(List.of(cash), BigDecimal.ZERO, new BigDecimal("6000"));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().displayName()).isEqualTo("Arvelduskonto");
  }

  @Test
  void formatCashAccountIdentifiesBanks() {
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("SEB deposit account").name())
        .isEqualTo("Arvelduskonto");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("SEB deposit account").institution())
        .isEqualTo("AS SEB Pank");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("Swedbank current").institution())
        .isEqualTo("Swedbank AS");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("LHV savings").institution())
        .isEqualTo("AS LHV Pank");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("Unknown bank").name())
        .isEqualTo("Arvelduskonto");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("Luminor current").institution())
        .isEqualTo("Luminor Bank AS");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount("Unknown bank").institution())
        .isEqualTo("Unknown bank");
    assertThat(InvestmentReportRowsBuilder.formatCashAccount(null).institution()).isNull();
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
}
