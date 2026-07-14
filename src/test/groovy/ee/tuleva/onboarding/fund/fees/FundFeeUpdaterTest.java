package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.MANAGEMENT_FEE;
import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.ONGOING_CHARGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.manager.FundManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundFeeUpdaterTest {

  @Mock private FundRepository fundRepository;

  private FundFeeUpdater updater;

  @BeforeEach
  void setUp() {
    updater = new FundFeeUpdater(fundRepository);
  }

  @Test
  void updatesOngoingChargesWhenValueChanged() {
    var fund = fund("EE1", "Tuleva Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(
        2,
        List.of(new PensionikeskusFeeRow("Tuleva Fund", new BigDecimal("0.0114"))),
        ONGOING_CHARGES);

    assertThat(fund.getOngoingChargesFigure()).isEqualByComparingTo("0.0114");
    assertThat(fund.getManagementFeeRate()).isEqualByComparingTo("0.0091");
    verify(fundRepository).save(fund);
  }

  @Test
  void updatesManagementFeeWhenValueChanged() {
    var fund = fund("EE1", "Tuleva Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(
        2,
        List.of(new PensionikeskusFeeRow("Tuleva Fund", new BigDecimal("0.0085"))),
        MANAGEMENT_FEE);

    assertThat(fund.getManagementFeeRate()).isEqualByComparingTo("0.0085");
    assertThat(fund.getOngoingChargesFigure()).isEqualByComparingTo("0.0050");
    verify(fundRepository).save(fund);
  }

  @Test
  void doesNotSaveWhenValueEqualIgnoringScale() {
    var fund = fund("EE1", "Tuleva Fund", 2, "0.0091", "0.005");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(
        2,
        List.of(new PensionikeskusFeeRow("Tuleva Fund", new BigDecimal("0.00500"))),
        ONGOING_CHARGES);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void matchesFundNamesNormalized() {
    var fund = fund("EE1", "Tuleva Maailma Aktsiate Pensionifond", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(
        2,
        List.of(
            new PensionikeskusFeeRow(
                "  tuleva   maailma aktsiate pensionifond ", new BigDecimal("0.0114"))),
        ONGOING_CHARGES);

    assertThat(fund.getOngoingChargesFigure()).isEqualByComparingTo("0.0114");
    verify(fundRepository).save(fund);
  }

  @Test
  void skipsValuesOutOfSanityBounds() {
    var zeroFund = fund("EE1", "Zero Fund", 2, "0.0091", "0.0050");
    var negativeFund = fund("EE2", "Negative Fund", 2, "0.0091", "0.0050");
    var highFund = fund("EE3", "High Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE))
        .willReturn(List.of(zeroFund, negativeFund, highFund));

    updater.update(
        2,
        List.of(
            new PensionikeskusFeeRow("Zero Fund", new BigDecimal("0")),
            new PensionikeskusFeeRow("Negative Fund", new BigDecimal("-0.001")),
            new PensionikeskusFeeRow("High Fund", new BigDecimal("0.0201"))),
        ONGOING_CHARGES);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void acceptsBoundaryValueOfTwoPercent() {
    var fund = fund("EE1", "Boundary Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(
        2,
        List.of(new PensionikeskusFeeRow("Boundary Fund", new BigDecimal("0.0200"))),
        ONGOING_CHARGES);

    assertThat(fund.getOngoingChargesFigure()).isEqualByComparingTo("0.0200");
    verify(fundRepository).save(fund);
  }

  @Test
  void ignoresActiveFundMissingFromRows() {
    var fund = fund("EE1", "Lonely Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(2, List.of(), ONGOING_CHARGES);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void ignoresRowMatchingNoFund() {
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of());

    updater.update(
        2,
        List.of(new PensionikeskusFeeRow("Ghost Fund", new BigDecimal("0.0114"))),
        ONGOING_CHARGES);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void continuesBatchWhenOneFundFails() {
    var firstFund = fund("EE1", "First Fund", 2, "0.0091", "0.0050");
    var secondFund = fund("EE2", "Second Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE))
        .willReturn(List.of(firstFund, secondFund));
    given(fundRepository.save(firstFund)).willThrow(new RuntimeException("db error"));

    updater.update(
        2,
        List.of(
            new PensionikeskusFeeRow("First Fund", new BigDecimal("0.0114")),
            new PensionikeskusFeeRow("Second Fund", new BigDecimal("0.0055"))),
        ONGOING_CHARGES);

    verify(fundRepository).save(secondFund);
  }

  @Test
  void skipsAmbiguousRowMatches() {
    var fund = fund("EE1", "Dup Fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE)).willReturn(List.of(fund));

    updater.update(
        2,
        List.of(
            new PensionikeskusFeeRow("Dup Fund", new BigDecimal("0.0114")),
            new PensionikeskusFeeRow("dup fund", new BigDecimal("0.0055"))),
        ONGOING_CHARGES);

    verify(fundRepository, never()).save(any());
  }

  @Test
  void skipsAmbiguousFundNames() {
    var firstFund = fund("EE1", "Dup Fund", 2, "0.0091", "0.0050");
    var secondFund = fund("EE2", "dup fund", 2, "0.0091", "0.0050");
    given(fundRepository.findAllByPillarAndStatus(2, ACTIVE))
        .willReturn(List.of(firstFund, secondFund));

    updater.update(
        2,
        List.of(new PensionikeskusFeeRow("Dup Fund", new BigDecimal("0.0114"))),
        ONGOING_CHARGES);

    verify(fundRepository, never()).save(any());
  }

  private static Fund fund(
      String isin,
      String nameEstonian,
      int pillar,
      String managementFeeRate,
      String ongoingChargesFigure) {
    return Fund.builder()
        .isin(isin)
        .nameEstonian(nameEstonian)
        .nameEnglish(nameEstonian)
        .shortName(isin)
        .pillar(pillar)
        .equityShare(BigDecimal.ZERO)
        .managementFeeRate(new BigDecimal(managementFeeRate))
        .ongoingChargesFigure(new BigDecimal(ongoingChargesFigure))
        .status(ACTIVE)
        .fundManager(FundManager.builder().id(1L).name("Tuleva").build())
        .inceptionDate(LocalDate.parse("2019-01-01"))
        .build();
  }
}
