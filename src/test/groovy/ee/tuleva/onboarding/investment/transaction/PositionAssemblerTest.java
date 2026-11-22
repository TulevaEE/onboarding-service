package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionAssemblerTest {

  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 1, 15);

  @Mock private FundPositionRepository fundPositionRepository;

  @InjectMocks private PositionAssembler assembler;

  @Test
  void assemble_populatesQuantityAndUnitPrice() {
    var position =
        FundPosition.builder()
            .accountId("IE00A")
            .fund(TUV100)
            .navDate(POSITION_DATE)
            .quantity(new BigDecimal("1000"))
            .marketPrice(new BigDecimal("500"))
            .marketValue(new BigDecimal("500000"))
            .build();
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(List.of(position));

    var result = assembler.assemble(TUV100, POSITION_DATE, PendingOrderImpact.none()).positions();

    assertThat(result)
        .containsExactly(
            new PositionSnapshot(
                "IE00A", new BigDecimal("500000"), new BigDecimal("1000"), new BigDecimal("500")));
  }

  @Test
  void sellableUnits_aReportRowWithoutUnitsIsKeptWithoutUnitsSoItsSaleIsSizedOnItsAmount() {
    var position =
        FundPosition.builder()
            .accountId("IE00A")
            .fund(TUV100)
            .navDate(POSITION_DATE)
            .marketValue(new BigDecimal("500000"))
            .build();
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(List.of(position));

    assertThat(assembler.assemble(TUV100, POSITION_DATE, PendingOrderImpact.none()).sellableUnits())
        .isEqualTo(Map.of("IE00A", new SellableUnits(null, ZERO)));
  }

  @Test
  void assemble_unitsInSalesTheReportDoesNotShowComeOffAtTheReportsOwnUnitValue() {
    givenReport(row("IE00A", "1000", "100000"));

    var assembled =
        assembler.assemble(
            TUV100, POSITION_DATE, sales(Map.of("IE00A", "600"), Map.of("IE00A", "54600")));

    assertThat(assembled.positions())
        .usingRecursiveComparison(byValue())
        .isEqualTo(
            List.of(
                new PositionSnapshot(
                    "IE00A", new BigDecimal("40000"), new BigDecimal("400"), null)));
    assertThat(assembled.deductedSales())
        .usingRecursiveComparison(byValue())
        .isEqualTo(
            List.of(
                new DeductedSale(
                    "IE00A",
                    new BigDecimal("1000"),
                    new BigDecimal("100000"),
                    new BigDecimal("600"),
                    new BigDecimal("60000"),
                    DeductedSale.DeductedBy.UNITS)));
  }

  @Test
  void assemble_moreUnitsInSalesThanTheReportHoldsLeaveNothingOfThePosition() {
    givenReport(row("IE00A", "1000", "100000"));

    var assembled =
        assembler.assemble(TUV100, POSITION_DATE, sales(Map.of("IE00A", "1200"), Map.of()));

    assertThat(assembled.positions())
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.marketValue()).isEqualByComparingTo(ZERO);
              assertThat(position.quantity()).isEqualByComparingTo(ZERO);
            });
  }

  @Test
  void assemble_aReportRowWithoutUnitsLosesTheValueOfSalesInTheirSettlementWindowAndSaysSo() {
    givenReport(row("IE00A", null, "100000"));

    var assembled =
        assembler.assemble(
            TUV100, POSITION_DATE, sales(Map.of("IE00A", "600"), Map.of("IE00A", "54600")));

    assertThat(assembled.positions())
        .usingRecursiveComparison(byValue())
        .isEqualTo(List.of(new PositionSnapshot("IE00A", new BigDecimal("45400"), null, null)));
    assertThat(assembled.deductedSales())
        .usingRecursiveComparison(byValue())
        .isEqualTo(
            List.of(
                new DeductedSale(
                    "IE00A",
                    null,
                    new BigDecimal("100000"),
                    new BigDecimal("600"),
                    new BigDecimal("54600"),
                    DeductedSale.DeductedBy.VALUE)));
  }

  @Test
  void assemble_aPurchaseTheReportDoesNotShowIsAddedOnTopOfTheDeductedPosition() {
    givenReport(row("IE00A", "1000", "100000"));
    var pendingOrders =
        new PendingOrderImpact(
            new BigDecimal("20000"),
            ZERO,
            Map.of("IE00A", new BigDecimal("20000")),
            Map.of("IE00A", new BigDecimal("200")),
            ZERO,
            ZERO,
            Map.of("IE00A", new BigDecimal("600")),
            Map.of());

    var assembled = assembler.assemble(TUV100, POSITION_DATE, pendingOrders);

    assertThat(assembled.positions())
        .usingRecursiveComparison(byValue())
        .isEqualTo(
            List.of(
                new PositionSnapshot(
                    "IE00A", new BigDecimal("60000"), new BigDecimal("600"), null)));
  }

  @Test
  void assemble_twoReportRowsOfOneInstrumentAddUpToTheUnitsTheFundMaySell() {
    givenReport(row("IE00A", "600", "60000"), row("IE00A", "400", "40000"));

    var assembled =
        assembler.assemble(TUV100, POSITION_DATE, sales(Map.of("IE00A", "700"), Map.of()));

    assertThat(assembled.sellableUnits())
        .isEqualTo(
            Map.of("IE00A", new SellableUnits(new BigDecimal("1000"), new BigDecimal("700"))));
    assertThat(assembled.positions())
        .satisfiesExactly(
            first -> {
              assertThat(first.quantity()).isEqualByComparingTo(ZERO);
              assertThat(first.marketValue()).isEqualByComparingTo(ZERO);
            },
            second -> {
              assertThat(second.quantity()).isEqualByComparingTo("300");
              assertThat(second.marketValue()).isEqualByComparingTo("30000");
            });
    assertThat(assembled.deductedSales())
        .singleElement()
        .satisfies(
            deducted -> {
              assertThat(deducted.reportUnits()).isEqualByComparingTo("1000");
              assertThat(deducted.reportValue()).isEqualByComparingTo("100000");
              assertThat(deducted.valueDeducted()).isEqualByComparingTo("70000");
            });
  }

  @Test
  void assemble_withoutSalesTheReportDoesNotShowNothingIsDeducted() {
    givenReport(row("IE00A", "1000", "100000"));

    var assembled = assembler.assemble(TUV100, POSITION_DATE, PendingOrderImpact.none());

    assertThat(assembled.positions())
        .containsExactly(
            new PositionSnapshot("IE00A", new BigDecimal("100000"), new BigDecimal("1000"), null));
    assertThat(assembled.deductedSales()).isEmpty();
  }

  private static RecursiveComparisonConfiguration byValue() {
    return RecursiveComparisonConfiguration.builder()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .build();
  }

  private void givenReport(FundPosition... rows) {
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(List.of(rows));
  }

  private static FundPosition row(String isin, String quantity, String marketValue) {
    return FundPosition.builder()
        .fund(TUV100)
        .accountType(SECURITY)
        .accountId(isin)
        .quantity(quantity == null ? null : new BigDecimal(quantity))
        .marketValue(new BigDecimal(marketValue))
        .build();
  }

  private static PendingOrderImpact sales(
      Map<String, String> unitsInSales, Map<String, String> valuesInTheWindow) {
    return new PendingOrderImpact(
        ZERO,
        ZERO,
        Map.of(),
        Map.of(),
        ZERO,
        ZERO,
        decimals(unitsInSales),
        decimals(valuesInTheWindow));
  }

  private static Map<String, BigDecimal> decimals(Map<String, String> values) {
    return values.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, entry -> new BigDecimal(entry.getValue())));
  }

  @Test
  void assemble_withASecurityRowWithoutAnIsin_stillAppliesTheOtherPendingPositions() {
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(SECURITY)
                    .accountId("IE00A")
                    .quantity(new BigDecimal("1000"))
                    .marketValue(new BigDecimal("500000"))
                    .build(),
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(SECURITY)
                    .marketValue(new BigDecimal("250000"))
                    .build()));
    var pendingOrders =
        new PendingOrderImpact(
            new BigDecimal("40000"),
            ZERO,
            Map.of("IE00A", new BigDecimal("40000")),
            Map.of(),
            ZERO,
            ZERO,
            Map.of(),
            Map.of());

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders).positions();

    assertThat(result)
        .extracting(PositionSnapshot::isin, PositionSnapshot::marketValue)
        .containsExactly(
            tuple("IE00A", new BigDecimal("540000")), tuple(null, new BigDecimal("250000")));
  }

  @Test
  void assemble_withASentSellOfAnIsinAbsentFromTheSebReport_addsNoPositionForIt() {
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(SECURITY)
                    .accountId("IE00A")
                    .quantity(new BigDecimal("1000"))
                    .marketValue(new BigDecimal("500000"))
                    .build()));
    var pendingOrders =
        new PendingOrderImpact(
            ZERO,
            new BigDecimal("1000"),
            Map.of(),
            Map.of(),
            ZERO,
            ZERO,
            Map.of("IE00B", new BigDecimal("20")),
            Map.of("IE00B", new BigDecimal("1000")));

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders).positions();

    assertThat(result)
        .singleElement()
        .satisfies(position -> assertThat(position.isin()).isEqualTo("IE00A"));
  }

  @Test
  void assemble_leavesPositionsWithoutAnUnsettledOrderExactlyAsReported() {
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(SECURITY)
                    .accountId("IE00A")
                    .quantity(new BigDecimal("1000"))
                    .marketValue(new BigDecimal("500000"))
                    .build(),
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(SECURITY)
                    .accountId("IE00B")
                    .quantity(new BigDecimal("200"))
                    .marketValue(new BigDecimal("100000"))
                    .build()));
    var pendingOrders =
        new PendingOrderImpact(
            new BigDecimal("40000"),
            ZERO,
            Map.of("IE00A", new BigDecimal("40000")),
            Map.of("IE00A", new BigDecimal("80")),
            ZERO,
            ZERO,
            Map.of(),
            Map.of());

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders).positions();

    assertThat(result)
        .filteredOn(position -> position.isin().equals("IE00B"))
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.marketValue()).isEqualByComparingTo(new BigDecimal("100000"));
              assertThat(position.quantity()).isEqualByComparingTo(new BigDecimal("200"));
            });
  }

  @Test
  void assemble_withASentBuyOfANewInstrument_createsThePositionItIsNotYetReportedIn() {
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                POSITION_DATE, TUV100, SECURITY))
        .willReturn(List.of());
    var pendingOrders =
        new PendingOrderImpact(
            new BigDecimal("25000"),
            ZERO,
            Map.of("IE00NEW", new BigDecimal("25000")),
            Map.of(),
            ZERO,
            ZERO,
            Map.of(),
            Map.of());

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders).positions();

    assertThat(result)
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.isin()).isEqualTo("IE00NEW");
              assertThat(position.marketValue()).isEqualByComparingTo(new BigDecimal("25000"));
            });
  }
}
