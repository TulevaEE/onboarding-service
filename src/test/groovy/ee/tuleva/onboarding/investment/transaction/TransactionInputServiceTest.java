package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.calculation.InvestmentPositionCalculation;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.*;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
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
class TransactionInputServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 1, 15);

  @Mock private PositionCalculationRepository positionCalculationRepository;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private FundLimitRepository fundLimitRepository;
  @Mock private PositionLimitRepository positionLimitRepository;
  @Mock private LedgerBalanceRepository ledgerBalanceRepository;
  @Mock private TransactionOrderRepository orderRepository;

  @InjectMocks private TransactionInputService service;

  @Test
  void gatherInput_assemblesAllInputs() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));

    var position =
        InvestmentPositionCalculation.builder()
            .isin("IE00A")
            .fund(TUV100)
            .date(positionDate)
            .calculatedMarketValue(new BigDecimal("500000"))
            .build();
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of(position));

    var cashPosition =
        FundPosition.builder()
            .fund(TUV100)
            .accountType(CASH)
            .marketValue(new BigDecimal("100000"))
            .build();
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of(cashPosition));

    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("5000"));

    var modelAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .weight(new BigDecimal("1.00"))
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100))
        .thenReturn(List.of(modelAllocation));

    var fundLimit =
        FundLimit.builder()
            .fund(TUV100)
            .reserveSoft(new BigDecimal("20000"))
            .minTransaction(new BigDecimal("5000"))
            .build();
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.of(fundLimit));

    var positionLimit =
        PositionLimit.builder()
            .fund(TUV100)
            .isin("IE00A")
            .softLimitPercent(new BigDecimal("0.50"))
            .hardLimitPercent(new BigDecimal("0.60"))
            .build();
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of(positionLimit));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.fund()).isEqualTo(TUV100);
    assertThat(result.positions()).hasSize(1);
    assertThat(result.positions().getFirst().isin()).isEqualTo("IE00A");
    assertThat(result.positions().getFirst().marketValue())
        .isEqualByComparingTo(new BigDecimal("500000"));
    assertThat(result.modelWeights()).hasSize(1);
    assertThat(result.grossPortfolioValue()).isEqualByComparingTo(new BigDecimal("600000"));
    assertThat(result.cashBuffer()).isEqualByComparingTo(new BigDecimal("20000"));
    assertThat(result.minTransactionThreshold()).isEqualByComparingTo(new BigDecimal("5000"));
    assertThat(result.positionLimits()).containsKey("IE00A");
    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("5000"));
  }

  @Test
  void gatherInput_withNoPositionDate_throwsException() {
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void gatherInput_withNoCash_usesZero() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.grossPortfolioValue()).isEqualByComparingTo(ZERO);
    assertThat(result.freeCash()).isEqualByComparingTo(ZERO);
  }

  @Test
  void gatherInput_withFastSellAllocations_returnsFastSellIsins() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var fastAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00FAST")
            .weight(new BigDecimal("0.50"))
            .fastSell(true)
            .build();
    var normalAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00SLOW")
            .weight(new BigDecimal("0.50"))
            .fastSell(false)
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100))
        .thenReturn(List.of(fastAllocation, normalAllocation));

    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.fastSellIsins()).containsExactly("IE00FAST");
  }

  @Test
  void gatherInput_withInstrumentTypeAndVenue_populatesModelWeights() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var etfAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00ETF")
            .weight(new BigDecimal("0.60"))
            .instrumentType(InstrumentType.ETF)
            .orderVenue(OrderVenue.SEB)
            .build();
    var fundAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("LU00FUND")
            .weight(new BigDecimal("0.40"))
            .instrumentType(InstrumentType.FUND)
            .orderVenue(OrderVenue.FT)
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100))
        .thenReturn(List.of(etfAllocation, fundAllocation));

    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.modelWeights()).hasSize(2);
    assertThat(result.instrumentTypes()).containsEntry("IE00ETF", InstrumentType.ETF);
    assertThat(result.instrumentTypes()).containsEntry("LU00FUND", InstrumentType.FUND);
    assertThat(result.orderVenues()).containsEntry("IE00ETF", OrderVenue.SEB);
    assertThat(result.orderVenues()).containsEntry("LU00FUND", OrderVenue.FT);
  }

  @Test
  void gatherInput_forTKF100_includesLedgerBalancesInLiabilities() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TKF100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));

    var position =
        InvestmentPositionCalculation.builder()
            .isin("IE00A")
            .fund(TKF100)
            .date(positionDate)
            .calculatedMarketValue(new BigDecimal("500000"))
            .build();
    when(positionCalculationRepository.findByFundAndDate(TKF100, positionDate))
        .thenReturn(List.of(position));

    var cashPosition =
        FundPosition.builder()
            .fund(TKF100)
            .accountType(CASH)
            .marketValue(new BigDecimal("200000"))
            .build();
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TKF100, CASH))
        .thenReturn(List.of(cashPosition));

    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TKF100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("3000"));

    when(modelPortfolioAllocationRepository.findLatestByFund(TKF100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TKF100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TKF100)).thenReturn(List.of());

    when(ledgerBalanceRepository.getIncomingPaymentsClearing()).thenReturn(new BigDecimal("10000"));
    when(ledgerBalanceRepository.getUnreconciledBankReceipts()).thenReturn(new BigDecimal("2000"));
    when(ledgerBalanceRepository.getFundUnitsReservedValue()).thenReturn(new BigDecimal("5000"));

    var result = service.gatherInput(TKF100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("10000"));
    assertThat(result.receivables()).isEqualByComparingTo(new BigDecimal("10000"));
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("200000"));
  }

  @Test
  void gatherInput_forNonTKF100_doesNotQueryLedger() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("1000"));
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(result.receivables()).isEqualByComparingTo(ZERO);
    org.mockito.Mockito.verifyNoInteractions(ledgerBalanceRepository);
  }

  @Test
  void gatherInput_withManualAdjustments_overridesValues() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("1000"));
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    Map<String, Object> adjustments =
        Map.of(
            "additionalLiabilities", 5000,
            "additionalReceivables", 3000);

    var result = service.gatherInput(TUV100, AS_OF_DATE, adjustments);

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("6000"));
    assertThat(result.receivables()).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("-3000"));
  }

  @Test
  void gatherInput_withNullReserveSoft_usesDefaultCashBuffer() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var fundLimit = FundLimit.builder().fund(TUV100).reserveSoft(null).minTransaction(null).build();
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.of(fundLimit));
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.cashBuffer()).isEqualByComparingTo(ZERO);
    assertThat(result.minTransactionThreshold()).isEqualByComparingTo(new BigDecimal("50000"));
  }

  @Test
  void gatherInput_withNullMarketValues_filtersOutPositions() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));

    var positionWithValue =
        InvestmentPositionCalculation.builder()
            .isin("IE00A")
            .fund(TUV100)
            .date(positionDate)
            .calculatedMarketValue(new BigDecimal("300000"))
            .build();
    var positionWithoutValue =
        InvestmentPositionCalculation.builder()
            .isin("IE00B")
            .fund(TUV100)
            .date(positionDate)
            .calculatedMarketValue(null)
            .build();
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of(positionWithValue, positionWithoutValue));

    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.positions())
        .isEqualTo(List.of(new PositionSnapshot("IE00A", new BigDecimal("300000"))));
    assertThat(result.grossPortfolioValue()).isEqualByComparingTo(new BigDecimal("300000"));
  }

  @Test
  void gatherInput_withInvalidManualAdjustment_throwsIllegalArgumentException() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    Map<String, Object> adjustments = Map.of("additionalLiabilities", "not-a-number");

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, adjustments))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gatherInput_withNullIsinAllocations_filtersThemOut() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var allocationWithIsin =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .weight(new BigDecimal("0.80"))
            .build();
    var allocationWithoutIsin =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin(null)
            .weight(new BigDecimal("0.20"))
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100))
        .thenReturn(List.of(allocationWithIsin, allocationWithoutIsin));

    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.modelWeights())
        .isEqualTo(List.of(new ModelWeight("IE00A", new BigDecimal("0.80"))));
  }

  @Test
  void gatherInput_withUnsettledOrders_subtractsPendingCashFromFreeCash() {
    var positionDate = AS_OF_DATE;
    when(positionCalculationRepository.getLatestDateUpTo(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(positionCalculationRepository.findByFundAndDate(TUV100, positionDate))
        .thenReturn(List.of());
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(
            positionDate, TUV100, CASH))
        .thenReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(CASH)
                    .marketValue(new BigDecimal("100000"))
                    .build()));
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFund(TUV100)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFund(TUV100)).thenReturn(Optional.empty());
    when(positionLimitRepository.findLatestByFund(TUV100)).thenReturn(List.of());

    var pendingBuyOrder =
        TransactionOrder.builder()
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("30000"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.SENT)
            .expectedSettlementDate(AS_OF_DATE.plusDays(2))
            .build();
    var pendingSellOrder =
        TransactionOrder.builder()
            .fund(TUV100)
            .instrumentIsin("IE00B")
            .transactionType(TransactionType.SELL)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("10000"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.SENT)
            .expectedSettlementDate(AS_OF_DATE.plusDays(2))
            .build();
    when(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .thenReturn(List.of(pendingBuyOrder, pendingSellOrder));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    // freeCash = cash(100000) - cashBuffer(0) - liabilities(0) + receivables(0)
    //            - pendingBuys(30000) + pendingSells(10000) = 80000
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("80000"));
  }
}
