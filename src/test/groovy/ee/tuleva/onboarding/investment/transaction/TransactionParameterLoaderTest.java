package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.PositionLimitRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionParameterLoaderTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 1, 15);

  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private FundLimitRepository fundLimitRepository;
  @Mock private PositionLimitRepository positionLimitRepository;

  @InjectMocks private TransactionParameterLoader loader;

  @Test
  void load_rejectsModelWeightsThatDoNotSumToOne() {
    var underweightAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .weight(new BigDecimal("0.90"))
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(underweightAllocation));

    assertThatThrownBy(() -> loader.load(TUV100, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Model weights do not sum to 1")
        .hasMessageContaining("fund=TUV100")
        .hasMessageContaining("sum=0.90");
  }

  @Test
  void load_withFastSellAllocations_returnsFastSellIsins() {
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
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(fastAllocation, normalAllocation));
    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var result = loader.load(TUV100, AS_OF_DATE);

    assertThat(result.fastSellIsins()).containsExactly("IE00FAST");
  }

  @Test
  void load_mergesFastSellFromPreviousAllocations() {
    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .fastSell(false)
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .fastSell(true)
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(previousAllocation));

    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var result = loader.load(TUV100, AS_OF_DATE);

    assertThat(result.fastSellIsins()).containsExactly("IE00OLD");
  }

  @Test
  void load_withInstrumentTypeAndVenue_populatesModelWeights() {
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
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(etfAllocation, fundAllocation));
    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var result = loader.load(TUV100, AS_OF_DATE);

    assertThat(result.modelWeights()).hasSize(2);
    assertThat(result.instrumentTypes()).containsEntry("IE00ETF", InstrumentType.ETF);
    assertThat(result.instrumentTypes()).containsEntry("LU00FUND", InstrumentType.FUND);
    assertThat(result.orderVenues()).containsEntry("IE00ETF", OrderVenue.SEB);
    assertThat(result.orderVenues()).containsEntry("LU00FUND", OrderVenue.FT);
  }

  @Test
  void load_throwsWhenFundLimitMissing() {
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of());
    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> loader.load(TUV100, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No fund limit found")
        .hasMessageContaining("fund=TUV100");
  }

  @Test
  void load_throwsWhenReserveSoftNull() {
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of());

    var fundLimit = FundLimit.builder().fund(TUV100).reserveSoft(null).minTransaction(ZERO).build();
    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(fundLimit));

    assertThatThrownBy(() -> loader.load(TUV100, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Fund limit field is missing")
        .hasMessageContaining("field=reserveSoft");
  }

  @Test
  void load_throwsWhenMinTransactionNull() {
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of());

    var fundLimit = FundLimit.builder().fund(TUV100).reserveSoft(ZERO).minTransaction(null).build();
    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(fundLimit));

    assertThatThrownBy(() -> loader.load(TUV100, AS_OF_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Fund limit field is missing")
        .hasMessageContaining("field=minTransaction");
  }

  @Test
  void load_includesInRunoffInstrumentTypesAndOrderVenues() {
    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00NEW")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.ETF)
            .orderVenue(OrderVenue.SEB)
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00OLD")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.FUND)
            .orderVenue(OrderVenue.FT)
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(previousAllocation));

    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var result = loader.load(TUV100, AS_OF_DATE);

    assertThat(result.instrumentTypes())
        .containsEntry("IE00NEW", InstrumentType.ETF)
        .containsEntry("IE00OLD", InstrumentType.FUND);
    assertThat(result.orderVenues())
        .containsEntry("IE00NEW", OrderVenue.SEB)
        .containsEntry("IE00OLD", OrderVenue.FT);
  }

  @Test
  void load_currentAllocationOverridesPreviousInstrumentTypeAndVenue() {
    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00SAME")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.ETF)
            .orderVenue(OrderVenue.SEB)
            .build();
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(currentAllocation));

    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00SAME")
            .weight(new BigDecimal("1.00"))
            .instrumentType(InstrumentType.FUND)
            .orderVenue(OrderVenue.FT)
            .build();
    given(modelPortfolioAllocationRepository.findPreviousByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(previousAllocation));

    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var result = loader.load(TUV100, AS_OF_DATE);

    assertThat(result.instrumentTypes()).containsEntry("IE00SAME", InstrumentType.ETF);
    assertThat(result.orderVenues()).containsEntry("IE00SAME", OrderVenue.SEB);
  }

  @Test
  void load_withNullIsinAllocations_filtersThemOut() {
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
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(List.of(allocationWithIsin, allocationWithoutIsin));

    given(fundLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE))
        .willReturn(Optional.of(zeroFundLimit(TUV100)));
    given(positionLimitRepository.findLatestByFundAsOf(TUV100, AS_OF_DATE)).willReturn(List.of());

    var result = loader.load(TUV100, AS_OF_DATE);

    assertThat(result.modelWeights())
        .isEqualTo(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))));
  }

  private static FundLimit zeroFundLimit(TulevaFund fund) {
    return FundLimit.builder().fund(fund).reserveSoft(ZERO).minTransaction(ZERO).build();
  }
}
