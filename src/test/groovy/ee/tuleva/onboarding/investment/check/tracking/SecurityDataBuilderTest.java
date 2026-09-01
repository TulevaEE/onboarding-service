package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityDataBuilderTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 10);
  private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 4, 9);

  @Mock PositionPriceResolver positionPriceResolver;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  private SecurityDataBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new SecurityDataBuilder(positionPriceResolver, publicHolidays);
  }

  private void givenPrice(String isin, LocalDate date, String price) {
    given(positionPriceResolver.resolve(eq(isin), eq(date), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal(price))
                    .validationStatus(ValidationStatus.OK)
                    .priceDate(date)
                    .build()));
  }

  private ModelPortfolioAllocation allocation(String isin, String weight, LocalDate date) {
    return ModelPortfolioAllocation.builder()
        .fund(TUK75)
        .isin(isin)
        .weight(new BigDecimal(weight))
        .effectiveDate(date)
        .build();
  }

  private FundPosition position(String isin, LocalDate date, String marketValue) {
    return FundPosition.builder()
        .fund(TUK75)
        .navDate(date)
        .accountType(SECURITY)
        .accountId(isin)
        .accountName(isin)
        .marketValue(new BigDecimal(marketValue))
        .build();
  }

  @Test
  void buildSecurityDataUsesModelWeightAndComputesActualWeightFromPositions() {
    var allocations =
        List.of(allocation("ISIN_A", "0.6", CHECK_DATE), allocation("ISIN_B", "0.4", CHECK_DATE));
    var positions =
        List.of(position("ISIN_A", CHECK_DATE, "600000"), position("ISIN_B", CHECK_DATE, "400000"));
    givenPrice("ISIN_A", CHECK_DATE, "102.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");
    givenPrice("ISIN_B", CHECK_DATE, "51.00");
    givenPrice("ISIN_B", PREVIOUS_DATE, "50.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            List.of(),
            positions,
            new BigDecimal("1000000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result).hasSize(2);
    var a = result.stream().filter(s -> s.isin().equals("ISIN_A")).findFirst().orElseThrow();
    assertThat(a.modelWeight()).isEqualByComparingTo("0.6");
    assertThat(a.actualWeight()).isEqualByComparingTo("0.600000");
  }

  @Test
  void buildSecurityDataSkipsAllocationsWithNullIsin() {
    var allocations =
        List.of(
            allocation("ISIN_A", "0.5", CHECK_DATE),
            ModelPortfolioAllocation.builder()
                .fund(TUK75)
                .isin(null)
                .weight(new BigDecimal("0.5"))
                .effectiveDate(CHECK_DATE)
                .build());
    givenPrice("ISIN_A", CHECK_DATE, "100.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            List.of(),
            List.of(position("ISIN_A", CHECK_DATE, "500000")),
            new BigDecimal("1000000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result).extracting(SecurityData::isin).containsExactly("ISIN_A");
  }

  @Test
  void buildSecurityDataIgnoresPositionsWithNullAccountId() {
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var positions =
        List.of(
            position("ISIN_A", CHECK_DATE, "500000"),
            FundPosition.builder()
                .fund(TUK75)
                .navDate(CHECK_DATE)
                .accountType(SECURITY)
                .accountId(null)
                .accountName("unknown")
                .marketValue(new BigDecimal("500000"))
                .build());
    givenPrice("ISIN_A", CHECK_DATE, "100.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            List.of(),
            positions,
            new BigDecimal("1000000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().actualWeight()).isEqualByComparingTo("0.500000");
  }

  @Test
  void buildSecurityDataUsesFirstPositionWhenAccountIdDuplicated() {
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var positions =
        List.of(position("ISIN_A", CHECK_DATE, "111"), position("ISIN_A", CHECK_DATE, "999"));
    givenPrice("ISIN_A", CHECK_DATE, "100.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            List.of(),
            positions,
            new BigDecimal("1000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result.getFirst().actualWeight()).isEqualByComparingTo("0.111000");
  }

  @Test
  void buildSecurityDataAddsZeroWeightEntryForStillHeldRemovedAllocation() {
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var previousAllocations =
        List.of(
            allocation("ISIN_A", "0.6", PREVIOUS_DATE), allocation("ISIN_B", "0.4", PREVIOUS_DATE));
    var positions =
        List.of(position("ISIN_A", CHECK_DATE, "600000"), position("ISIN_B", CHECK_DATE, "400000"));
    givenPrice("ISIN_A", CHECK_DATE, "100.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");
    givenPrice("ISIN_B", CHECK_DATE, "50.00");
    givenPrice("ISIN_B", PREVIOUS_DATE, "50.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            previousAllocations,
            positions,
            new BigDecimal("1000000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result).hasSize(2);
    var b = result.stream().filter(s -> s.isin().equals("ISIN_B")).findFirst().orElseThrow();
    assertThat(b.modelWeight()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(b.actualWeight()).isEqualByComparingTo("0.400000");
  }

  @Test
  void buildSecurityDataOmitsRemovedAllocationNoLongerHeld() {
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var previousAllocations =
        List.of(
            allocation("ISIN_A", "0.6", PREVIOUS_DATE), allocation("ISIN_B", "0.4", PREVIOUS_DATE));
    var positions = List.of(position("ISIN_A", CHECK_DATE, "1000000"));
    givenPrice("ISIN_A", CHECK_DATE, "100.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            previousAllocations,
            positions,
            new BigDecimal("1000000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result).extracting(SecurityData::isin).containsExactly("ISIN_A");
  }

  @Test
  void buildSecurityDataSkipsPreviousAllocationsWithNullIsin() {
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var previousAllocations =
        List.of(
            ModelPortfolioAllocation.builder()
                .fund(TUK75)
                .isin(null)
                .weight(new BigDecimal("0.4"))
                .effectiveDate(PREVIOUS_DATE)
                .build());
    var positions = List.of(position("ISIN_A", CHECK_DATE, "1000000"));
    givenPrice("ISIN_A", CHECK_DATE, "100.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");

    var result =
        builder.buildSecurityData(
            TUK75,
            allocations,
            previousAllocations,
            positions,
            new BigDecimal("1000000"),
            CHECK_DATE,
            PREVIOUS_DATE);

    assertThat(result).extracting(SecurityData::isin).containsExactly("ISIN_A");
  }

  @Test
  void blendTransitionWeightsReturnsSecuritiesUnchangedWhenIsinSetIsStable() {
    var snapshot = new PriceSnapshot(BigDecimal.TEN, CHECK_DATE);
    var securities =
        List.of(
            new SecurityData(
                "ISIN_A", new BigDecimal("0.5"), new BigDecimal("0.55"), snapshot, snapshot));
    var allocations = List.of(allocation("ISIN_A", "0.5", CHECK_DATE));
    var previousAllocations = List.of(allocation("ISIN_A", "0.5", PREVIOUS_DATE));
    var positions = List.of(position("ISIN_A", CHECK_DATE, "550000"));

    var result =
        builder.blendTransitionWeights(
            securities, allocations, previousAllocations, positions, TUK75);

    assertThat(result).isEqualTo(securities);
  }

  @Test
  void blendTransitionWeightsUsesActualWeightForTransitioningIsin() {
    var snapshot = new PriceSnapshot(BigDecimal.TEN, CHECK_DATE);
    var securities =
        List.of(
            new SecurityData(
                "ISIN_A", new BigDecimal("0.3"), new BigDecimal("0.35"), snapshot, snapshot),
            new SecurityData(
                "ISIN_B", new BigDecimal("0.7"), new BigDecimal("0.65"), snapshot, snapshot));
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var previousAllocations =
        List.of(
            allocation("ISIN_A", "0.3", PREVIOUS_DATE), allocation("ISIN_B", "0.7", PREVIOUS_DATE));
    var positions =
        List.of(position("ISIN_A", CHECK_DATE, "350000"), position("ISIN_B", CHECK_DATE, "650000"));

    var result =
        builder.blendTransitionWeights(
            securities, allocations, previousAllocations, positions, TUK75);

    var blended = result.stream().filter(s -> s.isin().equals("ISIN_B")).findFirst().orElseThrow();
    assertThat(blended.modelWeight()).isEqualByComparingTo("0.65");
    // The transition leg takes 0.65, so the settled leg gives up exactly that much: it is scaled
    // from 0.3 to 1 - 0.65 = 0.35. Keeping its original 0.3 would leave the model weighing 0.95.
    var unchanged =
        result.stream().filter(s -> s.isin().equals("ISIN_A")).findFirst().orElseThrow();
    assertThat(unchanged.modelWeight()).isEqualByComparingTo("0.35");
    assertThat(result.stream().map(SecurityData::modelWeight).reduce(ZERO, BigDecimal::add))
        .isEqualByComparingTo("1.00");
  }

  @Test
  void blendTransitionWeightsBlendsBothAddedAndRemovedIsinsDuringAFullSwap() {
    var snapshot = new PriceSnapshot(BigDecimal.TEN, CHECK_DATE);
    var securities =
        List.of(
            new SecurityData("ISIN_A", BigDecimal.ZERO, new BigDecimal("0.4"), snapshot, snapshot),
            new SecurityData(
                "ISIN_B", new BigDecimal("1.0"), new BigDecimal("0.6"), snapshot, snapshot));
    var allocations = List.of(allocation("ISIN_A", "1.0", CHECK_DATE));
    var previousAllocations = List.of(allocation("ISIN_B", "1.0", PREVIOUS_DATE));
    var positions =
        List.of(position("ISIN_A", CHECK_DATE, "400000"), position("ISIN_B", CHECK_DATE, "600000"));

    var result =
        builder.blendTransitionWeights(
            securities, allocations, previousAllocations, positions, TUK75);

    var a = result.stream().filter(s -> s.isin().equals("ISIN_A")).findFirst().orElseThrow();
    assertThat(a.modelWeight()).isEqualByComparingTo("0.4");
    var b = result.stream().filter(s -> s.isin().equals("ISIN_B")).findFirst().orElseThrow();
    assertThat(b.modelWeight()).isEqualByComparingTo("0.6");
  }

  @Test
  void buildBodHoldingsReturnsNullWhenAPricedPositionHasNoIsin() {
    var positions =
        List.of(
            FundPosition.builder()
                .fund(TUK75)
                .navDate(PREVIOUS_DATE)
                .accountType(SECURITY)
                .accountId(null)
                .accountName("unknown")
                .marketValue(new BigDecimal("500000"))
                .build());

    var result =
        builder.buildBodHoldings(
            TUK75, CHECK_DATE, PREVIOUS_DATE, positions, new BigDecimal("500000"));

    assertThat(result).isNull();
  }

  @Test
  void buildBodHoldingsReturnsNullWhenAPriceIsMissing() {
    var positions = List.of(position("ISIN_A", PREVIOUS_DATE, "500000"));
    given(positionPriceResolver.resolve(eq("ISIN_A"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(Optional.empty());
    given(positionPriceResolver.resolve(eq("ISIN_A"), eq(PREVIOUS_DATE), any(Instant.class)))
        .willReturn(Optional.empty());

    var result =
        builder.buildBodHoldings(
            TUK75, CHECK_DATE, PREVIOUS_DATE, positions, new BigDecimal("500000"));

    assertThat(result).isNull();
  }

  @Test
  void buildBodHoldingsReturnsHoldingsWhenPricesArePresent() {
    var positions = List.of(position("ISIN_A", PREVIOUS_DATE, "500000"));
    givenPrice("ISIN_A", CHECK_DATE, "102.00");
    givenPrice("ISIN_A", PREVIOUS_DATE, "100.00");

    var result =
        builder.buildBodHoldings(
            TUK75, CHECK_DATE, PREVIOUS_DATE, positions, new BigDecimal("500000"));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().isin()).isEqualTo("ISIN_A");
    assertThat(result.getFirst().weight()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void isStaleDateIsFalseWhenSnapshotDateIsNull() {
    var snapshot = new PriceSnapshot(BigDecimal.TEN, null);
    assertThat(SecurityDataBuilder.isStaleDate(snapshot, CHECK_DATE)).isFalse();
  }

  @Test
  void isStaleDateIsFalseWhenSnapshotDateMatchesExpected() {
    var snapshot = new PriceSnapshot(BigDecimal.TEN, CHECK_DATE);
    assertThat(SecurityDataBuilder.isStaleDate(snapshot, CHECK_DATE)).isFalse();
  }

  @Test
  void isStaleDateIsTrueWhenSnapshotDateDiffersFromExpected() {
    var snapshot = new PriceSnapshot(BigDecimal.TEN, PREVIOUS_DATE);
    assertThat(SecurityDataBuilder.isStaleDate(snapshot, CHECK_DATE)).isTrue();
  }

  @Test
  void resolvePriceSnapshotDropsPriceWhenValidationStatusIsNotOk() {
    given(positionPriceResolver.resolve(eq("ISIN_A"), eq(CHECK_DATE), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("100.00"))
                    .validationStatus(ValidationStatus.PRICE_DISCREPANCY)
                    .priceDate(CHECK_DATE)
                    .build()));

    var result = builder.resolvePriceSnapshot("ISIN_A", CHECK_DATE, Instant.now());

    assertThat(result.price()).isNull();
    assertThat(result.date()).isNull();
  }

  @Test
  void resolvePriceSnapshotKeepsPriceWhenValidationStatusIsOk() {
    givenPrice("ISIN_A", CHECK_DATE, "105.00");

    var result = builder.resolvePriceSnapshot("ISIN_A", CHECK_DATE, Instant.now());

    assertThat(result.price()).isEqualByComparingTo("105.00");
    assertThat(result.date()).isEqualTo(CHECK_DATE);
  }
}
