package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalEligibilityServiceTest {

  Clock clock = Clock.fixed(Instant.parse("2024-10-10T10:00:00Z"), UTC);

  @Mock private EpisService episService;

  @InjectMocks private WithdrawalEligibilityService withdrawalEligibilityService;

  @BeforeEach
  void setup() {
    ClockHolder.setClock(clock);
  }

  @AfterEach
  void cleanup() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those over 60")
  void shouldCalculateWithdrawalEligibilityForThoseOver60() {
    var aPerson = sampleRetirementAgePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(30));

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
    assertThat(result.pillarWithdrawalEligibility().second()).isTrue();
    assertThat(result.pillarWithdrawalEligibility().third()).isTrue();
    assertThat(result.recommendedDurationYears()).isEqualTo(30);
    assertThat(result.age()).isNotNull();
    assertThat(result.arrestsOrBankruptciesPresent()).isFalse();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.pillarWithdrawalEligibility().second()).isFalse();
    assertThat(result.pillarWithdrawalEligibility().third()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isNotNull();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60 with arrests")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60WithArrest() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(true, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.pillarWithdrawalEligibility().second()).isFalse();
    assertThat(result.pillarWithdrawalEligibility().third()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isNotNull();
    assertThat(result.arrestsOrBankruptciesPresent()).isTrue();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60 with bankruptcies")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60WithBankruptcy() {
    var aPerson = samplePerson;
    var aContactDetails = contactDetailsFixture();
    aContactDetails.setPersonalCode(aPerson.getPersonalCode());

    when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, true));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.pillarWithdrawalEligibility().second()).isFalse();
    assertThat(result.pillarWithdrawalEligibility().third()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isNotNull();
    assertThat(result.arrestsOrBankruptciesPresent()).isTrue();
  }

  @Nested
  @DisplayName("third pillar early withdrawal")
  class ThirdPillarEarlyWithdrawal {

    @AfterEach
    void cleanup() {
      ClockHolder.setDefaultClock();
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for 5 years and III pillar opened before 2021")
    void shouldCalculateWithdrawalEligibilityEarlyThirdPillar() {
      Clock clock2028 = Clock.fixed(Instant.parse("2028-04-01T10:00:00Z"), UTC);
      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37212305258")
              .firstName("Jürto")
              .lastName("Nii-Dommzonn")
              .build();
      ClockHolder.setClock(clock2028);

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.pillarWithdrawalEligibility().second()).isFalse();
      assertThat(result.pillarWithdrawalEligibility().third()).isTrue();
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isNotNull();
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for less than 5 years and III pillar opened before 2021")
    void shouldCalculateWithdrawalEligibilityEarlyThirdPillarLessThan5Years() {
      Clock clock2025 = Clock.fixed(Instant.parse("2025-04-01T10:00:00Z"), UTC);
      ClockHolder.setClock(clock2025);

      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37001275700")
              .firstName("Kert")
              .lastName("Ämmapets")
              .build();

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2020-12-01T00:00:00.000Z"));

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.pillarWithdrawalEligibility().second()).isFalse();
      assertThat(result.pillarWithdrawalEligibility().third()).isFalse();
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isNotNull();
    }

    @Test
    @DisplayName(
        "calculates withdrawal eligibility for those over 55 with III pillar for more than 5 years and III pillar opened after 2021")
    void shouldCalculateWithdrawalEligibilityEarlyThirdPillarAfter2021() {
      Clock clock2028 = Clock.fixed(Instant.parse("2028-04-01T10:00:00Z"), UTC);
      ClockHolder.setClock(clock2028);

      // 55 years old
      var aPerson =
          PersonImpl.builder()
              .personalCode("37212305258")
              .firstName("Jürto")
              .lastName("Nii-Dommzonn")
              .build();

      var aContactDetails = contactDetailsFixture();
      aContactDetails.setPersonalCode(aPerson.getPersonalCode());
      aContactDetails.setThirdPillarInitDate(Instant.parse("2021-01-10T00:00:00.000Z"));

      when(episService.getContactDetails(aPerson)).thenReturn(aContactDetails);

      when(episService.getFundPensionCalculation(aPerson))
          .thenReturn(new FundPensionCalculationDto(0));

      when(episService.getArrestsBankruptciesPresent(aPerson))
          .thenReturn(new ArrestsBankruptciesDto(false, false));

      var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

      assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
      assertThat(result.pillarWithdrawalEligibility().second()).isFalse();
      assertThat(result.pillarWithdrawalEligibility().third()).isFalse();
      assertThat(result.recommendedDurationYears()).isEqualTo(0);
      assertThat(result.age()).isNotNull();
    }
  }
}
