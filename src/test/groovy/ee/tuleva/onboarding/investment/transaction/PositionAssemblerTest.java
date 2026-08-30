package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    var result = assembler.assemble(TUV100, POSITION_DATE, PendingOrderImpact.none());

    assertThat(result)
        .containsExactly(
            new PositionSnapshot(
                "IE00A", new BigDecimal("500000"), new BigDecimal("1000"), new BigDecimal("500")));
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
            new BigDecimal("40000"), ZERO, Map.of("IE00A", new BigDecimal("40000")), Map.of());

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders);

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
            Map.of("IE00B", new BigDecimal("-1000")),
            Map.of("IE00B", new BigDecimal("-20")));

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders);

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
            Map.of("IE00A", new BigDecimal("80")));

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders);

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
            new BigDecimal("25000"), ZERO, Map.of("IE00NEW", new BigDecimal("25000")), Map.of());

    var result = assembler.assemble(TUV100, POSITION_DATE, pendingOrders);

    assertThat(result)
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.isin()).isEqualTo("IE00NEW");
              assertThat(position.marketValue()).isEqualByComparingTo(new BigDecimal("25000"));
            });
  }
}
