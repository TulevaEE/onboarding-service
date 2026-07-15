package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.epis.FundCycleTimeline;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycle;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlowService;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriod;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.PevaRavaPhase;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16FundFlow;
import ee.tuleva.onboarding.investment.epis.R16Phase;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.*;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
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

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private FundLimitRepository fundLimitRepository;
  @Mock private PositionLimitRepository positionLimitRepository;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private FundValueRepository fundValueRepository;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private PevaRavaPeriodService pevaRavaPeriodService;
  @Mock private PevaRavaFlowService pevaRavaFlowService;
  @Mock private R45ReportService r45ReportService;
  @Mock private R16FlowCalculationService r16FlowCalculationService;
  @Mock private R16PhaseCalculator r16PhaseCalculator;
  @Mock private InvestmentParameterRepository investmentParameterRepository;

  @InjectMocks private TransactionInputService service;

  @Test
  void gatherInput_assemblesAllInputs() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));

    var position =
        FundPosition.builder()
            .accountId("IE00A")
            .fund(TUV100)
            .navDate(positionDate)
            .marketValue(new BigDecimal("500000"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of(position));

    var cashPosition =
        FundPosition.builder()
            .fund(TUV100)
            .accountType(CASH)
            .marketValue(new BigDecimal("100000"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
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
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(modelAllocation));

    var fundLimit =
        FundLimit.builder()
            .fund(TUV100)
            .reserveSoft(new BigDecimal("20000"))
            .minTransaction(new BigDecimal("5000"))
            .build();
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(fundLimit));

    var positionLimit =
        PositionLimit.builder()
            .fund(TUV100)
            .isin("IE00A")
            .softLimitPercent(new BigDecimal("0.50"))
            .hardLimitPercent(new BigDecimal("0.60"))
            .build();
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(positionLimit));

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
  void gatherInput_rejectsModelWeightsThatDoNotSumToOne() {
    var positionDate = AS_OF_DATE;
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(positionDate));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .willReturn(List.of());
    given(
            feeAccrualRepository.getAccruedFeesForMonth(
                eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .willReturn(ZERO);

    var underweightAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .weight(new BigDecimal("0.90"))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(underweightAllocation));

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Model weights do not sum to 1")
        .hasMessageContaining("fund=TUV100")
        .hasMessageContaining("sum=0.90");
  }

  @Test
  void gatherInput_withNoPositionDate_throwsException() {
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void gatherInput_withNoCash_usesZero() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.grossPortfolioValue()).isEqualByComparingTo(ZERO);
    assertThat(result.freeCash()).isEqualByComparingTo(ZERO);
  }

  @Test
  void gatherInput_withFastSellAllocations_returnsFastSellIsins() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
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
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(fastAllocation, normalAllocation));

    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.fastSellIsins()).containsExactly("IE00FAST");
  }

  @Test
  void gatherInput_mergesFastSellFromPreviousAllocations() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .fastSell(false)
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .fastSell(true)
            .build();
    when(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(previousAllocation));

    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.fastSellIsins()).containsExactly("IE00OLD");
  }

  @Test
  void gatherInput_withInstrumentTypeAndVenue_populatesModelWeights() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
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
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(etfAllocation, fundAllocation));

    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

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
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));

    var position =
        FundPosition.builder()
            .accountId("IE00A")
            .fund(TKF100)
            .navDate(positionDate)
            .marketValue(new BigDecimal("500000"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TKF100, SECURITY))
        .thenReturn(List.of(position));

    var cashPosition =
        FundPosition.builder()
            .fund(TKF100)
            .accountType(CASH)
            .marketValue(new BigDecimal("200000"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TKF100, CASH))
        .thenReturn(List.of(cashPosition));

    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TKF100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("3000"));

    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TKF100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TKF100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TKF100)));
    when(positionLimitRepository.findLatestByFundAsOf(TKF100, AS_OF_DATE)).thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance("INCOMING_PAYMENTS_CLEARING"))
        .thenReturn(new BigDecimal("10000"));
    when(navLedgerRepository.getSystemAccountBalance("UNRECONCILED_BANK_RECEIPTS"))
        .thenReturn(new BigDecimal("2000"));
    when(navLedgerRepository.getFundUnitsBalance("FUND_UNITS_RESERVED"))
        .thenReturn(new BigDecimal("100"));
    when(fundValueRepository.findLastValueForFund("EE0000003283"))
        .thenReturn(
            Optional.of(new FundValue("EE0000003283", null, new BigDecimal("50"), null, null)));

    var result = service.gatherInput(TKF100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("10000"));
    assertThat(result.receivables()).isEqualByComparingTo(new BigDecimal("10000"));
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("200000"));
  }

  @Test
  void gatherInput_forNonTKF100_doesNotQueryLedger() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("1000"));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(result.receivables()).isEqualByComparingTo(ZERO);
    org.mockito.Mockito.verifyNoInteractions(navLedgerRepository);
  }

  @Test
  void gatherInput_withManualAdjustments_overridesValues() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(new BigDecimal("1000"));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

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
  void gatherInput_throwsWhenFundLimitMissing() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No fund limit found")
        .hasMessageContaining("fund=TUV100");
  }

  @Test
  void gatherInput_throwsWhenReserveSoftNull() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());

    var fundLimit = FundLimit.builder().fund(TUV100).reserveSoft(null).minTransaction(ZERO).build();
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(fundLimit));

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Fund limit field is missing")
        .hasMessageContaining("field=reserveSoft");
  }

  @Test
  void gatherInput_throwsWhenMinTransactionNull() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());

    var fundLimit = FundLimit.builder().fund(TUV100).reserveSoft(ZERO).minTransaction(null).build();
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(fundLimit));

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Fund limit field is missing")
        .hasMessageContaining("field=minTransaction");
  }

  @Test
  void gatherInput_includesInRunoffInstrumentTypesAndOrderVenues() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.ETF)
            .orderVenue(OrderVenue.SEB)
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.FUND)
            .orderVenue(OrderVenue.FT)
            .build();
    when(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(previousAllocation));

    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.instrumentTypes())
        .containsEntry("IE00NEW", InstrumentType.ETF)
        .containsEntry("IE00OLD", InstrumentType.FUND);
    assertThat(result.orderVenues())
        .containsEntry("IE00NEW", OrderVenue.SEB)
        .containsEntry("IE00OLD", OrderVenue.FT);
  }

  @Test
  void gatherInput_currentAllocationOverridesPreviousInstrumentTypeAndVenue() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00SAME")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.ETF)
            .orderVenue(OrderVenue.SEB)
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00SAME")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.FUND)
            .orderVenue(OrderVenue.FT)
            .build();
    when(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(previousAllocation));

    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.instrumentTypes()).containsEntry("IE00SAME", InstrumentType.ETF);
    assertThat(result.orderVenues()).containsEntry("IE00SAME", OrderVenue.SEB);
  }

  private static FundLimit zeroFundLimit(TulevaFund fund) {
    return FundLimit.builder().fund(fund).reserveSoft(ZERO).minTransaction(ZERO).build();
  }

  @Test
  void gatherInput_withNullMarketValues_filtersOutPositions() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));

    var positionWithValue =
        FundPosition.builder()
            .accountId("IE00A")
            .fund(TUV100)
            .navDate(positionDate)
            .marketValue(new BigDecimal("300000"))
            .build();
    var positionWithoutValue =
        FundPosition.builder()
            .accountId("IE00B")
            .fund(TUV100)
            .navDate(positionDate)
            .marketValue(null)
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of(positionWithValue, positionWithoutValue));

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.positions())
        .isEqualTo(List.of(new PositionSnapshot("IE00A", new BigDecimal("300000"))));
    assertThat(result.grossPortfolioValue()).isEqualByComparingTo(new BigDecimal("300000"));
  }

  @Test
  void gatherInput_withInvalidManualAdjustment_throwsIllegalArgumentException() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    Map<String, Object> adjustments = Map.of("additionalLiabilities", "not-a-number");

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, adjustments))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gatherInput_withNullIsinAllocations_filtersThemOut() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .thenReturn(List.of());
    when(feeAccrualRepository.getAccruedFeesForMonth(
            eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .thenReturn(ZERO);

    var allocationWithIsin =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .weight(new BigDecimal("1.00"))
            .build();
    var allocationWithoutIsin =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin(null)
            .weight(new BigDecimal("0.20"))
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of(allocationWithIsin, allocationWithoutIsin));

    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.modelWeights())
        .isEqualTo(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))));
  }

  @Test
  void gatherInput_withUnsettledOrders_subtractsPendingCashFromFreeCash() {
    var positionDate = AS_OF_DATE;
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(positionDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
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
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .thenReturn(Optional.of(zeroFundLimit(TUV100)));
    when(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).thenReturn(List.of());

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

  @Test
  void gatherInput_withExecutedButUnsettledBuyOrder_subtractsItsAmountFromFreeCash() {
    var positionDate = AS_OF_DATE;
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(positionDate));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, SECURITY))
        .willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, TUV100, CASH))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .fund(TUV100)
                    .accountType(CASH)
                    .marketValue(new BigDecimal("100000"))
                    .build()));
    given(
            feeAccrualRepository.getAccruedFeesForMonth(
                eq(TUV100), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .willReturn(ZERO);
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of());
    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var executedUnsettledBuy =
        TransactionOrder.builder()
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("25000"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.EXECUTED)
            .expectedSettlementDate(AS_OF_DATE.plusDays(1))
            .build();
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(List.of(executedUnsettledBuy));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    // freeCash = cash(100000) - pendingBuys(25000) = 75000
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("75000"));
  }

  @Test
  void gatherInput_pevaRavaActiveBeforeSellBy_addsRawLiquidityToLiabilities() {
    stubEmptyBaseline(TulevaFund.TUK75);
    given(pevaRavaPeriodService.getCurrentPeriod(AS_OF_DATE))
        .willReturn(
            Optional.of(
                pevaRavaPeriod(PevaRavaPhase.ACTIVE, timeline(true, false), timeline(true, true))));
    given(pevaRavaFlowService.calculateFlows(AS_OF_DATE))
        .willReturn(
            Map.of(
                TulevaFund.TUK75, pevaRavaFlows(new BigDecimal("10000"), new BigDecimal("11000"))));

    var result = service.gatherInput(TulevaFund.TUK75, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("10000"));
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("-10000"));
  }

  @Test
  void gatherInput_pevaRavaActiveAfterSellBy_addsBufferedLiquidityToLiabilities() {
    stubEmptyBaseline(TulevaFund.TUK00);
    given(pevaRavaPeriodService.getCurrentPeriod(AS_OF_DATE))
        .willReturn(
            Optional.of(
                pevaRavaPeriod(
                    PevaRavaPhase.TUK00_ACTIVE, timeline(false, false), timeline(true, true))));
    given(pevaRavaFlowService.calculateFlows(AS_OF_DATE))
        .willReturn(
            Map.of(
                TulevaFund.TUK00, pevaRavaFlows(new BigDecimal("10000"), new BigDecimal("11000"))));

    var result = service.gatherInput(TulevaFund.TUK00, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("11000"));
  }

  @Test
  void gatherInput_pevaRavaFundNotYetActive_addsNothing() {
    stubEmptyBaseline(TulevaFund.TUK75);
    given(pevaRavaPeriodService.getCurrentPeriod(AS_OF_DATE))
        .willReturn(
            Optional.of(
                pevaRavaPeriod(
                    PevaRavaPhase.TUK00_ACTIVE, timeline(false, false), timeline(true, false))));

    var result = service.gatherInput(TulevaFund.TUK75, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
    org.mockito.Mockito.verifyNoInteractions(pevaRavaFlowService);
  }

  @Test
  void gatherInput_pevaRavaDonePhase_addsNothing() {
    stubEmptyBaseline(TulevaFund.TUK75);
    given(pevaRavaPeriodService.getCurrentPeriod(AS_OF_DATE))
        .willReturn(
            Optional.of(
                pevaRavaPeriod(PevaRavaPhase.DONE, timeline(true, true), timeline(true, true))));

    var result = service.gatherInput(TulevaFund.TUK75, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
    org.mockito.Mockito.verifyNoInteractions(pevaRavaFlowService);
  }

  @Test
  void gatherInput_pevaRavaFlowsMissingForFund_addsNothing() {
    stubEmptyBaseline(TulevaFund.TUK75);
    given(pevaRavaPeriodService.getCurrentPeriod(AS_OF_DATE))
        .willReturn(
            Optional.of(
                pevaRavaPeriod(PevaRavaPhase.ACTIVE, timeline(true, false), timeline(true, true))));
    given(pevaRavaFlowService.calculateFlows(AS_OF_DATE)).willReturn(Map.of());

    var result = service.gatherInput(TulevaFund.TUK75, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
  }

  @Test
  void gatherInput_forNonPevaRavaFund_doesNotQueryPevaRavaServices() {
    stubEmptyBaseline(TUV100);

    service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    org.mockito.Mockito.verifyNoInteractions(pevaRavaPeriodService, pevaRavaFlowService);
  }

  @Test
  void gatherInput_r45NetOutflow_addsAbsoluteNetToLiabilities() {
    stubEmptyBaseline(TUV100);
    given(r45ReportService.getLatestFlows())
        .willReturn(
            Map.of(
                TUV100,
                new R45Result(
                    new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("-4000"))));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("4000"));
    assertThat(result.receivables()).isEqualByComparingTo(ZERO);
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("-4000"));
  }

  @Test
  void gatherInput_r45NetInflow_addsNetToReceivables() {
    stubEmptyBaseline(TUV100);
    given(r45ReportService.getLatestFlows())
        .willReturn(
            Map.of(
                TUV100,
                new R45Result(
                    new BigDecimal("5000"), new BigDecimal("1000"), new BigDecimal("4000"))));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
    assertThat(result.receivables()).isEqualByComparingTo(new BigDecimal("4000"));
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("4000"));
  }

  @Test
  void gatherInput_r45DataMissingForFund_addsNothing() {
    stubEmptyBaseline(TUV100);
    given(r45ReportService.getLatestFlows()).willReturn(Map.of());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
    assertThat(result.receivables()).isEqualByComparingTo(ZERO);
  }

  @Test
  void gatherInput_r45SummaryIncomplete_throwsToBlockTradeSizing() {
    stubEmptyBaseline(TUV100);
    given(r45ReportService.getIncompleteFunds()).willReturn(List.of(TUV100));

    assertThatThrownBy(() -> service.gatherInput(TUV100, AS_OF_DATE, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("R45 summary incomplete");
  }

  @Test
  void gatherInput_r45NetOutflowCombinesWithManualAdjustments() {
    stubEmptyBaseline(TUV100);
    given(r45ReportService.getLatestFlows())
        .willReturn(
            Map.of(
                TUV100,
                new R45Result(
                    new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("-4000"))));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of("additionalLiabilities", 2000));

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("6000"));
  }

  @Test
  void gatherInput_r16ActivePhase_addsTotalOutflowToLiabilities() {
    stubEmptyBaseline(TUV100);
    var flow = r16Flow(TUV100, new BigDecimal("10500"));
    given(r16FlowCalculationService.calculateFlows(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(flow));
    given(r16PhaseCalculator.phaseFor(flow, AS_OF_DATE)).willReturn(R16Phase.ACTIVE);

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("10500"));
    assertThat(result.freeCash()).isEqualByComparingTo(new BigDecimal("-10500"));
  }

  @Test
  void gatherInput_r16BufferedPhase_addsBufferedRoundedOutflowToLiabilities() {
    stubEmptyBaseline(TUV100);
    var flow = r16Flow(TUV100, new BigDecimal("10500"));
    given(r16FlowCalculationService.calculateFlows(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(flow));
    given(r16PhaseCalculator.phaseFor(flow, AS_OF_DATE)).willReturn(R16Phase.BUFFERED);
    given(investmentParameterRepository.findLatestValue(R16_BUFFER_PERCENT, AS_OF_DATE))
        .willReturn(new BigDecimal("0.02"));
    given(investmentParameterRepository.findLatestValue(R16_ROUNDING_STEP, AS_OF_DATE))
        .willReturn(new BigDecimal("1000"));

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    // 10500 * 1.02 = 10710 -> rounded up to step 1000 -> 11000
    assertThat(result.liabilities()).isEqualByComparingTo(new BigDecimal("11000"));
  }

  @Test
  void gatherInput_r16VisiblePhase_addsNothing() {
    stubEmptyBaseline(TUV100);
    var flow = r16Flow(TUV100, new BigDecimal("10500"));
    given(r16FlowCalculationService.calculateFlows(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(flow));
    given(r16PhaseCalculator.phaseFor(flow, AS_OF_DATE)).willReturn(R16Phase.VISIBLE);

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
    org.mockito.Mockito.verifyNoInteractions(investmentParameterRepository);
  }

  @Test
  void gatherInput_r16IgnorePhase_addsNothing() {
    stubEmptyBaseline(TUV100);
    var flow = r16Flow(TUV100, new BigDecimal("10500"));
    given(r16FlowCalculationService.calculateFlows(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(flow));
    given(r16PhaseCalculator.phaseFor(flow, AS_OF_DATE)).willReturn(R16Phase.IGNORE);

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
  }

  @Test
  void gatherInput_r16DataMissing_addsNothingAndSkipsPhaseCalculation() {
    stubEmptyBaseline(TUV100);
    given(r16FlowCalculationService.calculateFlows(TUV100, AS_OF_DATE))
        .willReturn(Optional.empty());

    var result = service.gatherInput(TUV100, AS_OF_DATE, Map.of());

    assertThat(result.liabilities()).isEqualByComparingTo(ZERO);
    org.mockito.Mockito.verifyNoInteractions(r16PhaseCalculator);
  }

  @Test
  void gatherInput_forNonR16Fund_doesNotQueryR16Services() {
    stubEmptyBaseline(TKF100);
    given(navLedgerRepository.getSystemAccountBalance("UNRECONCILED_BANK_RECEIPTS"))
        .willReturn(ZERO);
    given(navLedgerRepository.getSystemAccountBalance("INCOMING_PAYMENTS_CLEARING"))
        .willReturn(ZERO);
    given(navLedgerRepository.getFundUnitsBalance("FUND_UNITS_RESERVED")).willReturn(ZERO);

    service.gatherInput(TKF100, AS_OF_DATE, Map.of());

    org.mockito.Mockito.verifyNoInteractions(r16FlowCalculationService, r16PhaseCalculator);
  }

  private static R16FundFlow r16Flow(TulevaFund fund, BigDecimal totalOutflowEur) {
    return new R16FundFlow(
        fund,
        new BigDecimal("10000"),
        new BigDecimal("500"),
        totalOutflowEur,
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 15),
        LocalDate.of(2026, 1, 8));
  }

  private void stubEmptyBaseline(TulevaFund fund) {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, AS_OF_DATE))
        .willReturn(Optional.of(AS_OF_DATE));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF_DATE, fund, SECURITY))
        .willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(AS_OF_DATE, fund, CASH))
        .willReturn(List.of());
    given(
            feeAccrualRepository.getAccruedFeesForMonth(
                eq(fund), any(), eq(List.of(FeeType.MANAGEMENT, FeeType.DEPOT)), any()))
        .willReturn(ZERO);
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, AS_OF_DATE))
        .willReturn(List.of());
    given(fundLimitRepository.findLatestByFundAsOf(fund, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(fund)));
    given(positionLimitRepository.findLatestByFundAsOf(fund, AS_OF_DATE)).willReturn(List.of());
  }

  private static PevaRavaPeriod pevaRavaPeriod(
      PevaRavaPhase phase, FundCycleTimeline tuk75, FundCycleTimeline tuk00) {
    var cycle = new PevaRavaCycle(LocalDate.of(2025, 11, 30), LocalDate.of(2026, 1, 2));
    return new PevaRavaPeriod(phase, cycle, tuk75, tuk00);
  }

  private static FundCycleTimeline timeline(boolean dActive, boolean sellByReached) {
    return new FundCycleTimeline(
        LocalDate.of(2025, 12, 22), LocalDate.of(2025, 12, 29), dActive, sellByReached);
  }

  private static PevaRavaFlows pevaRavaFlows(
      BigDecimal liquidityRequired, BigDecimal tradeBufferedLiquidity) {
    return new PevaRavaFlows(
        liquidityRequired,
        ZERO,
        ZERO,
        liquidityRequired,
        liquidityRequired,
        tradeBufferedLiquidity,
        tradeBufferedLiquidity);
  }
}
