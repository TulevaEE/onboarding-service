package ee.tuleva.onboarding.party;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import ee.tuleva.onboarding.aml.RegisterRequester;
import ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwner;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.populationregister.PopulationRegisterException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
class AutomaticGuardianLookupsTest {

  private static final String REQUESTER = "requester-as-designated";
  private static final String NEXT_REQUESTER = "next-requester-as-designated";
  private static final String DESIGNATED_PERSONAL_CODE = "38812121215";
  private static final Instant NOW = Instant.parse("2026-11-19T03:45:00Z");
  private static final LocalDate EARLIER = LocalDate.of(2026, 11, 18);
  private static final LocalDate LATEST = LocalDate.of(2026, 11, 19);

  private static final String NEW_MINOR = "61506150006";
  private static final String MINOR_WITH_A_PAYOUT = "61508110000";
  private static final String LOOKED_UP_MINOR = "61603140009";
  private static final String KNOWN_MINOR_WITH_AN_OLD_PAYOUT = "61603140009";
  private static final String NEW_ADULT = "38812121215";

  @Autowired private UnitOwnerRepository unitOwners;
  @Autowired private AnalyticsThirdPillarTransactionRepository transactions;
  @Autowired private GuardianLookupRepository guardianLookups;
  @Autowired private JdbcClient database;

  private final MinorGuardianLookupService service = mock(MinorGuardianLookupService.class);
  private final RegisterRequester registerRequester = mock(RegisterRequester.class);

  // A new minor holder, a minor with a payout this week, a new adult holder and a new minor holder
  // looked up already.
  void newHoldersAndARecentPayout() {
    holder(NEW_MINOR, LATEST);
    holder(NEW_ADULT, LATEST);
    holder(LOOKED_UP_MINOR, LATEST);
    holder(MINOR_WITH_A_PAYOUT, EARLIER);
    holder(MINOR_WITH_A_PAYOUT, LATEST);
    payout(MINOR_WITH_A_PAYOUT, LATEST.minusDays(2));
    guardianLookups.save(
        new GuardianLookup(LOOKED_UP_MINOR, LATEST, NOW, UUID.randomUUID(), Set.of()));
  }

  @BeforeEach
  void designateTheRequester() {
    given(registerRequester.designatedXRoadUserId()).willReturn(Optional.of(REQUESTER));
  }

  @Test
  void looksUpEachNewMinorHolderAndMinorWithARecentPayoutOnceAsTheDesignatedRequester() {
    newHoldersAndARecentPayout();

    lookups().onUnitOwnerSnapshot();

    verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
    verify(service).lookUpChildOnce(REQUESTER, MINOR_WITH_A_PAYOUT);
    for (String other : new String[] {LOOKED_UP_MINOR, NEW_ADULT}) {
      verify(service, never()).lookUpChildOnce(anyString(), eq(other));
    }
  }

  @Test
  void asksAmlServiceForTheRequesterOnceASnapshotRunNotOnceAChild() {
    newHoldersAndARecentPayout();

    lookups().onUnitOwnerSnapshot();

    verify(registerRequester, times(1)).designatedXRoadUserId();
  }

  @Test
  void asksAmlServiceForTheRequesterAtEachRunSoANewDesignationTakesEffectWithoutARestart() {
    given(registerRequester.designatedXRoadUserId())
        .willReturn(Optional.of(REQUESTER), Optional.of(NEXT_REQUESTER));
    AutomaticGuardianLookups lookups = lookups();

    lookups.lookUpOnce(NEW_MINOR);
    lookups.lookUpOnce(MINOR_WITH_A_PAYOUT);

    verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
    verify(service).lookUpChildOnce(NEXT_REQUESTER, MINOR_WITH_A_PAYOUT);
  }

  @Test
  void looksUpNoHolderSeenBeforeWithoutAPayoutThisWeek() {
    holder(KNOWN_MINOR_WITH_AN_OLD_PAYOUT, EARLIER);
    holder(KNOWN_MINOR_WITH_AN_OLD_PAYOUT, LATEST);
    payout(KNOWN_MINOR_WITH_AN_OLD_PAYOUT, LATEST.minusDays(30));

    assertThat(lookups().candidates()).isEmpty();
  }

  @Test
  void logsARejectedLookupWithTheRegistersStatusAndCodeAndCountsItApart(CapturedOutput output) {
    newHoldersAndARecentPayout();
    var rejection =
        HttpClientErrorException.create(
            FORBIDDEN,
            "Forbidden",
            new HttpHeaders(),
            ("{\"status\": 403, \"code\": \"FORBIDDEN\", \"title\": \"Forbidden\","
                    + " \"detail\": \"Pärija ei tohi pärida isikut "
                    + NEW_MINOR
                    + "\","
                    + " \"traceId\": \"abc123\"}")
                .getBytes(UTF_8),
            UTF_8);
    given(service.lookUpChildOnce(REQUESTER, NEW_MINOR))
        .willThrow(new PopulationRegisterException("rejected", rejection));

    lookups().onUnitOwnerSnapshot();

    assertThat(output.getAll())
        .contains("status=403, code=FORBIDDEN, title=Forbidden, traceId=abc123")
        .contains("children=2, lookedUp=1, failed=1")
        .doesNotContain(NEW_MINOR, MINOR_WITH_A_PAYOUT, REQUESTER);
  }

  @Test
  void logsAFailedLookupAtAnEventAndNeverThrowsIt(CapturedOutput output) {
    given(service.lookUpChildOnce(REQUESTER, NEW_MINOR))
        .willThrow(new PopulationRegisterException("no person"));

    lookups().lookUpOnce(NEW_MINOR);

    assertThat(output.getAll())
        .contains("Automatic guardian lookup failed")
        .contains("error=PopulationRegisterException, status=none")
        .doesNotContain(NEW_MINOR);
  }

  @Test
  void looksUpNoOneWhileAmlServiceDesignatesNoRequesterAndSaysSoWithoutCodes(
      CapturedOutput output) {
    newHoldersAndARecentPayout();
    given(registerRequester.designatedXRoadUserId()).willReturn(Optional.empty());

    lookups().onUnitOwnerSnapshot();
    lookups().lookUpOnce(NEW_MINOR);

    verifyNoInteractions(service);
    assertThat(output.getAll())
        .contains("no requester designated in aml-service")
        .doesNotContain(NEW_MINOR, MINOR_WITH_A_PAYOUT);
  }

  @Test
  void looksUpNoOneWhenAmlServiceCannotNameTheRequesterAndLogsAnErrorWithoutItsAnswer(
      CapturedOutput output) {
    newHoldersAndARecentPayout();
    given(registerRequester.designatedXRoadUserId())
        .willThrow(
            HttpServerErrorException.create(
                INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                new HttpHeaders(),
                ("{\"personalCode\": \"" + DESIGNATED_PERSONAL_CODE + "\"}").getBytes(UTF_8),
                UTF_8));

    lookups().onUnitOwnerSnapshot();
    lookups().lookUpOnce(NEW_MINOR);

    verifyNoInteractions(service);
    assertThat(output.getAll())
        .contains("ERROR")
        .doesNotContain(DESIGNATED_PERSONAL_CODE, NEW_MINOR, MINOR_WITH_A_PAYOUT);
  }

  @Test
  void looksUpOneChildOnceAtAnEventAsTheDesignatedRequesterAsItIs() {
    lookups().lookUpOnce(NEW_MINOR);

    verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
  }

  @Test
  void looksUpNoMoreThanFiftyChildrenARun() {
    newHoldersAndARecentPayout();
    assertThat(AutomaticGuardianLookups.MAX_PER_RUN).isEqualTo(50);
    assertThat(lookups().candidates()).containsExactlyInAnyOrder(NEW_MINOR, MINOR_WITH_A_PAYOUT);
  }

  @Test
  void backfillsMinorThirdPillarHoldersAfterTheNewHoldersAtTheSnapshotAsTheDesignatedRequester() {
    holder(NEW_MINOR, LATEST);

    lookups().onUnitOwnerSnapshot();

    var inOrder = inOrder(service);
    inOrder.verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
    inOrder.verify(service).backfillMinorThirdPillarHolders(REQUESTER, 1000, Duration.ZERO);
    assertThat(AutomaticGuardianLookups.MAX_BACKFILL_PER_RUN).isEqualTo(1000);
  }

  @Test
  void backfillsUntilTheLastDayOf2026AndStopsByItselfAfterIt() {
    Instant lastSecondOf2026 = Instant.parse("2026-12-31T23:59:59Z");

    lookupsAt(lastSecondOf2026).onUnitOwnerSnapshot();
    lookupsAt(lastSecondOf2026.plusSeconds(1)).onUnitOwnerSnapshot();

    verify(service).backfillMinorThirdPillarHolders(REQUESTER, 1000, Duration.ZERO);
  }

  @Test
  void triesTheBackfillAgainAtTheNextSnapshotWhenItCannotStart() {
    given(service.backfillMinorThirdPillarHolders(REQUESTER, 1000, Duration.ZERO))
        .willThrow(new MinorGuardianLookupAlreadyRunning());

    assertThatNoException().isThrownBy(() -> lookups().onUnitOwnerSnapshot());
  }

  @Test
  void startsNoBackfillOnceTheSnapshotListenerIsInterrupted() {
    Thread.currentThread().interrupt();
    try {
      lookups().onUnitOwnerSnapshot();
    } finally {
      Thread.interrupted();
    }

    verify(service, never()).backfillMinorThirdPillarHolders(anyString(), anyInt(), any());
  }

  @Test
  void backfillsABatchOnDemandAsTheDesignatedRequesterAndReturnsItsCounts() {
    given(service.backfillMinorThirdPillarHolders(REQUESTER, 5, Duration.ZERO))
        .willReturn(new MinorGuardianLookupRun(5, 0, 5409));

    assertThat(lookups().backfillNow(5)).isEqualTo(new MinorGuardianLookupRun(5, 0, 5409));
  }

  @Test
  void refusesAnOnDemandBackfillWhileAmlServiceDesignatesNoRequester() {
    given(registerRequester.designatedXRoadUserId()).willReturn(Optional.empty());

    assertThatThrownBy(() -> lookups().backfillNow(5))
        .isInstanceOf(MinorGuardianBackfillUnavailable.class);
    verifyNoInteractions(service);
  }

  @Test
  void refusesAnOnDemandBackfillWhenAmlServiceCannotNameTheRequester() {
    given(registerRequester.designatedXRoadUserId())
        .willThrow(new ResourceAccessException("aml-service did not answer"));

    assertThatThrownBy(() -> lookups().backfillNow(5))
        .isInstanceOf(MinorGuardianBackfillUnavailable.class);
    verifyNoInteractions(service);
  }

  @Test
  void refusesAnOnDemandBackfillAfterTheLastDayOf2026WithoutAskingForARequester() {
    AutomaticGuardianLookups afterTheLastDay = lookupsAt(Instant.parse("2027-01-01T00:00:00Z"));

    assertThatThrownBy(() -> afterTheLastDay.backfillNow(5))
        .isInstanceOf(MinorGuardianBackfillUnavailable.class);
    verify(registerRequester, never()).designatedXRoadUserId();
    verifyNoInteractions(service);
  }

  private AutomaticGuardianLookups lookups() {
    return lookupsAt(NOW);
  }

  private AutomaticGuardianLookups lookupsAt(Instant now) {
    Clock at = Clock.fixed(now, ZoneOffset.UTC);
    return new AutomaticGuardianLookups(
        service,
        new AutomaticGuardianLookupCandidates(database, at),
        guardianLookups,
        registerRequester,
        at,
        Duration.ZERO);
  }

  private void holder(String code, LocalDate snapshot) {
    unitOwners.save(
        UnitOwner.builder()
            .personalId(code)
            .snapshotDate(snapshot)
            .dateCreated(LocalDateTime.of(2026, 11, 19, 0, 0))
            .build());
  }

  private void payout(String code, LocalDate day) {
    transactions.save(
        AnalyticsThirdPillarTransaction.builder()
            .reportingDate(day)
            .fullName("Synthetic Child")
            .personalId(code)
            .accountNo("EE000000")
            .transactionType("Lunastamine")
            .shareAmount(BigDecimal.ONE.negate())
            .sharePrice(BigDecimal.ONE)
            .nav(BigDecimal.ONE)
            .transactionValue(BigDecimal.TEN.negate())
            .dateCreated(LocalDateTime.of(2026, 11, 19, 0, 0))
            .build());
  }
}
