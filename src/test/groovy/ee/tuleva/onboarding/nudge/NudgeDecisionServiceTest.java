package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notConverted;
import static ee.tuleva.onboarding.nudge.NudgeContext.SAVINGS_FUND_PAYMENT;
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_PAYMENT;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.conversion.PendingMandateApplications;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NudgeDecisionServiceTest {

  @Mock private PillarStatus pillarStatus;
  @Mock private UserConversionService conversionService;
  @Mock private PendingMandateApplications pendingApplications;
  @Mock private SecondPillarPaymentRateService paymentRateService;
  @Mock private SecondPillarLeaverStatus leaverStatus;
  @Mock private RecurringContributionStatus recurringStatus;
  @Mock private SavingsFundSaverStatus saverStatus;
  @Mock private TaxHeadroom taxHeadroom;
  @Mock private SavingsFundFeeRate savingsFundFeeRate;
  @Mock private FeeComparisonCalculator feeComparisonCalculator;
  @Mock private ActingParties actingParties;
  @Mock private SecurityContextRunner securityContextRunner;
  @Mock private OfflineNudgeInputs offlineInputs;

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private NudgeDecisionService service;

  private final User member = sampleUser().build();
  private final NudgeAccount self = NudgeAccount.self(member);
  private final NudgeAccount child = NudgeAccount.person("51111111111");
  private final NudgeAccount company = NudgeAccount.company("12345678");

  private NudgeDecisionService serviceOn(String date) {
    Clock clock =
        Clock.fixed(LocalDateTime.parse(date + "T09:00:00").atZone(TALLINN).toInstant(), TALLINN);
    return new NudgeDecisionService(
        new NudgeInputsAssembler(
            pillarStatus,
            conversionService,
            pendingApplications,
            paymentRateService,
            feeComparisonCalculator,
            new KnownLookups(
                leaverStatus,
                recurringStatus,
                saverStatus,
                taxHeadroom,
                actingParties,
                savingsFundFeeRate),
            new PaymentRateSeasons(
                clock, new MandateDeadlinesService(clock, new PublicHolidays()))),
        offlineInputs,
        securityContextRunner);
  }

  @BeforeEach
  void setUp() {
    service = serviceOn("2026-09-10");
    lenient()
        .when(securityContextRunner.callAs(any(), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<?>>getArgument(1).get());
    lenient().when(pillarStatus.of(any())).thenReturn(new PillarActivity(true, true));
    lenient().when(conversionService.getConversion(any())).thenReturn(fullyConverted());
    lenient().when(paymentRateService.getPaymentRates(any())).thenReturn(new PaymentRates(6, null));
    lenient().when(pendingApplications.getPendingExchanges(any(), any())).thenReturn(List.of());
    lenient().when(pendingApplications.hasPendingWithdrawals(any(), any())).thenReturn(false);
    lenient().when(leaverStatus.hasLeft(any())).thenReturn(false);
    lenient().when(recurringStatus.thirdPillar(any())).thenReturn(true);
    lenient().when(recurringStatus.savingsFund(any())).thenReturn(true);
    lenient().when(saverStatus.savesFor(any())).thenReturn(true);
    lenient().when(taxHeadroom.hasHeadroom(any())).thenReturn(false);
    lenient().when(savingsFundFeeRate.ongoingChargesPercent()).thenReturn(new BigDecimal("0.28"));
    lenient()
        .when(feeComparisonCalculator.forSecondPillar(any(), any()))
        .thenReturn(Optional.empty());
    lenient().when(actingParties.representedBy(any())).thenReturn(List.of());
  }

  @Test
  void aKnownSavingsFundFeeGoesIntoTheSavingsFundNudge() {
    given(saverStatus.savesFor(any())).willReturn(false);

    assertThat(service.decide(member, self, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.savingsFund(new BigDecimal("0.28")));
  }

  @Test
  void aFailedSavingsFundFeeLookupSkipsTheSavingsFundNudgeInsteadOfFailing() {
    given(saverStatus.savesFor(any())).willReturn(false);
    given(savingsFundFeeRate.ongoingChargesPercent())
        .willThrow(new RuntimeException("fund row missing"));

    assertThat(service.decide(member, self, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
  }

  @Test
  void looksTheInputsUpAsTheUserThemselvesRegardlessOfTheActingRole() {
    service.decide(member, child, SAVINGS_FUND_PAYMENT);

    verify(securityContextRunner).callAs(eq(member), any());
  }

  @Test
  void theOfflineDecisionNeverMintsASecurityContextOrTouchesEpisBackedInputs() {
    given(
            offlineInputs.assemble(
                OfflineSaver.of(member), NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(NudgeInputsFixture.everythingSorted().member(false).build());

    assertThat(service.decideOffline(member, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .isEqualTo(NudgeDecision.of(NudgeKey.MEMBERSHIP));
    org.mockito.Mockito.verifyNoInteractions(
        securityContextRunner, pillarStatus, conversionService, paymentRateService, taxHeadroom);
  }

  @Test
  void aSaverWithEverythingInPlaceGetsNoNudge() {
    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
  }

  @Test
  void aFailedLeaverLookupSkipsTheSecondPillarNudgeInsteadOfGuessing() {
    User nonMember = sampleUserNonMember().build();
    given(pillarStatus.of(any())).willReturn(new PillarActivity(false, true));
    given(leaverStatus.hasLeft(any())).willThrow(new IllegalStateException("warehouse down"));

    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
    assertThat(service.decide(nonMember, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.MEMBERSHIP));
  }

  @Test
  void aFailedRecurringLookupNeverTurnsTheRecurringNudgeOn() {
    given(recurringStatus.thirdPillar(member.getPersonalCode()))
        .willThrow(new IllegalStateException("warehouse down"));

    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
  }

  @Test
  void theSavingsFundPaymentScopesTheStandingOrderNudgeToThePaidAccount() {
    given(recurringStatus.savingsFund(child)).willReturn(false);

    assertThat(service.decide(member, child, SAVINGS_FUND_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.SAVINGS_FUND_RECURRING));
    assertThat(service.decide(member, self, SAVINGS_FUND_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
  }

  @Test
  void decidesForARegistryOnlyPersonWithoutAnAccountFromTheRegistryAlone() {
    PersonImpl person =
        PersonImpl.builder()
            .personalCode("38801010004")
            .firstName("Registry")
            .lastName("Person")
            .build();
    given(
            offlineInputs.assemble(
                OfflineSaver.registryOnly(person), NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .willReturn(
            NudgeInputsFixture.everythingSorted()
                .secondPillarPartiallyConverted(false)
                .secondPillarFullyConverted(false)
                .build());

    assertThat(service.decideForRegistryOnly(person, NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED))
        .isEqualTo(NudgeDecision.secondPillarTransfer(null));
  }

  @Test
  void aCompanyPayerGoesThroughTheSameChainAsEveryoneElse() {
    given(recurringStatus.savingsFund(company)).willReturn(true);
    given(pillarStatus.of(member)).willReturn(new PillarActivity(false, false));

    assertThat(service.decide(member, company, SAVINGS_FUND_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.SECOND_PILLAR_START));
  }

  @Test
  void aSavingsFundPaymentMakesThePayerASaverEvenBeforeTheUnitsAreIssued() {
    given(recurringStatus.savingsFund(self)).willReturn(false);

    assertThat(service.decide(member, self, SAVINGS_FUND_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.SAVINGS_FUND_RECURRING));
  }

  @Test
  void savingThroughAChildCountsAsSavingSoTheSavingsFundIsNotSuggested() {
    given(saverStatus.savesFor(self)).willReturn(false);
    given(actingParties.representedBy(member.getPersonalCode())).willReturn(List.of(child));
    given(saverStatus.savesFor(child)).willReturn(true);

    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
  }

  @Test
  void aNonSaverIsNudgedToTheSavingsFundWithTheCurrentFee() {
    given(saverStatus.savesFor(any())).willReturn(false);

    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.savingsFund(new BigDecimal("0.28")));
  }

  @Test
  void taxHeadroomIsOnlyLookedUpForThirdPillarSavers() {
    given(pillarStatus.of(member)).willReturn(new PillarActivity(true, false));

    service.decide(member, THIRD_PILLAR_PAYMENT);

    verify(taxHeadroom, never()).hasHeadroom(any());
  }

  @Test
  void theFeeComparisonTravelsWithTheSecondPillarNudge() {
    var comparison = new FeeComparison(new BigDecimal("0.65"), 130, 56, 74);
    given(conversionService.getConversion(member)).willReturn(notConverted());
    given(feeComparisonCalculator.forSecondPillar(member, new BigDecimal("0.0051")))
        .willReturn(Optional.of(comparison));

    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.secondPillarTransfer(comparison));
  }

  @Test
  void aPendingSecondPillarTransferSuppressesTheTransferNudge() {
    given(conversionService.getConversion(member)).willReturn(notConverted());
    given(pendingApplications.getPendingExchanges(SECOND, member))
        .willReturn(
            List.of(
                org.mockito.Mockito.mock(ee.tuleva.onboarding.conversion.PendingExchange.class)));
    given(paymentRateService.getPaymentRates(member)).willReturn(new PaymentRates(2, null));

    assertThat(service.decide(member, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE));
  }

  @Test
  void aSaverWhoCanStillRaiseTheRateGetsTheRateNudgeWithTheSeasonOnTheAccountPage() {
    given(paymentRateService.getPaymentRates(member)).willReturn(new PaymentRates(2, null));

    assertThat(serviceOn("2026-11-10").decide(member, self, NudgeContext.ACCOUNT))
        .isEqualTo(
            NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE)
                .withPaymentRateSeason(
                    new PaymentRateSeason(
                        LocalDate.of(2026, 11, 30),
                        LocalDate.of(2027, 1, 1),
                        PaymentRateSeason.Mode.SEASON)));
  }

  @Test
  void aSaverAlreadyAtTheMaximumRateStillGetsTheSeasonBesideTheNudgeThatDidWin() {
    given(paymentRateService.getPaymentRates(member)).willReturn(new PaymentRates(6, null));
    given(pillarStatus.of(member)).willReturn(new PillarActivity(true, false));

    assertThat(serviceOn("2026-11-10").decide(member, self, NudgeContext.ACCOUNT))
        .isEqualTo(
            NudgeDecision.of(NudgeKey.THIRD_PILLAR_START)
                .withPaymentRateSeason(
                    new PaymentRateSeason(
                        LocalDate.of(2026, 11, 30),
                        LocalDate.of(2027, 1, 1),
                        PaymentRateSeason.Mode.SEASON)));
  }

  @Test
  void outsideTheSeasonTheAccountDecisionCarriesNoSeason() {
    given(paymentRateService.getPaymentRates(member)).willReturn(new PaymentRates(2, null));

    assertThat(serviceOn("2026-10-10").decide(member, self, NudgeContext.ACCOUNT))
        .isEqualTo(NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE));
  }

  @Test
  void inDecemberTheRateNudgeYieldsNothingAndNoSeasonIsCarried() {
    given(paymentRateService.getPaymentRates(member)).willReturn(new PaymentRates(2, null));

    assertThat(serviceOn("2026-12-10").decide(member, self, NudgeContext.ACCOUNT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
    assertThat(serviceOn("2026-12-10").decide(member, self, THIRD_PILLAR_PAYMENT))
        .isEqualTo(NudgeDecision.of(NudgeKey.NONE));
  }
}
