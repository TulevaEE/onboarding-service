package ee.tuleva.onboarding.party;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwner;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerBalanceEmbeddable;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.populationregister.PopulationRegisterException;
import ee.tuleva.onboarding.populationregister.PopulationRegisterUnavailable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
class AutomaticGuardianLookupsTest {

  private static final String REQUESTER = "requester-as-configured";
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
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private static final String MINOR_HOLDER = "61506150006";
  private static final String OTHER_MINOR_HOLDER = "61508110000";
  private static final String LOOKED_UP_MINOR_HOLDER = "61603140009";
  private static final String ADULT_HOLDER = "38812121215";
  private static final String MINOR_IN_THE_SECOND_PILLAR_ONLY = "61603140009";

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

  @Test
  void looksUpEachNewMinorHolderAndMinorWithARecentPayoutOnceAsTheConfiguredRequester() {
    newHoldersAndARecentPayout();

    lookups(REQUESTER).lookUpNewHoldersAndPayouts(REQUESTER);

    verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
    verify(service).lookUpChildOnce(REQUESTER, MINOR_WITH_A_PAYOUT);
    for (String other : new String[] {LOOKED_UP_MINOR, NEW_ADULT}) {
      verify(service, never()).lookUpChildOnce(anyString(), eq(other));
    }
  }

  @Test
  void looksUpNoHolderSeenBeforeWithoutAPayoutThisWeek() {
    holder(KNOWN_MINOR_WITH_AN_OLD_PAYOUT, EARLIER);
    holder(KNOWN_MINOR_WITH_AN_OLD_PAYOUT, LATEST);
    payout(KNOWN_MINOR_WITH_AN_OLD_PAYOUT, LATEST.minusDays(30));

    assertThat(lookups(REQUESTER).candidates()).isEmpty();
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

    lookups(REQUESTER).lookUpNewHoldersAndPayouts(REQUESTER);

    assertThat(output.getAll())
        .contains("status=403, code=FORBIDDEN, title=Forbidden, traceId=abc123")
        .contains("children=2, lookedUp=1, failed=1")
        .doesNotContain(NEW_MINOR, MINOR_WITH_A_PAYOUT, REQUESTER);
  }

  @Test
  void logsAFailedLookupAtAnEventAndNeverThrowsIt(CapturedOutput output) {
    given(service.lookUpChildOnce(REQUESTER, NEW_MINOR))
        .willThrow(new PopulationRegisterException("no person"));

    lookups(REQUESTER).lookUpOnce(NEW_MINOR);

    assertThat(output.getAll())
        .contains("Automatic guardian lookup failed")
        .contains("error=PopulationRegisterException, status=none")
        .doesNotContain(NEW_MINOR);
  }

  @Test
  void looksUpNoOneUntilARequesterIsConfiguredAndSaysSoWithoutCodes(CapturedOutput output) {
    minorAndAdultThirdPillarHolders();

    lookups(null).onUnitOwnerSnapshot();
    lookups(null).lookUpOnce(NEW_MINOR);

    verifyNoInteractions(service);
    assertThat(output.getAll())
        .contains("Automatic guardian lookup skipped, no requester configured")
        .doesNotContain(NEW_MINOR);
  }

  @Test
  void looksUpOneChildOnceAtAnEventAsTheConfiguredRequesterAsItIs() {
    lookups(REQUESTER).lookUpOnce(NEW_MINOR);

    verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
  }

  @Test
  void looksUpNoMoreThanFiftyChildrenARun() {
    newHoldersAndARecentPayout();
    assertThat(AutomaticGuardianLookups.MAX_PER_RUN).isEqualTo(50);
    assertThat(lookups(REQUESTER).candidates())
        .containsExactlyInAnyOrder(NEW_MINOR, MINOR_WITH_A_PAYOUT);
  }

  // Minor third pillar holders, one looked up already, beside an adult holder and a minor who
  // holds only second pillar units.
  void minorAndAdultThirdPillarHolders() {
    thirdPillarHolder(MINOR_HOLDER, LATEST);
    thirdPillarHolder(OTHER_MINOR_HOLDER, LATEST);
    thirdPillarHolder(LOOKED_UP_MINOR_HOLDER, LATEST);
    thirdPillarHolder(ADULT_HOLDER, LATEST);
    guardianLookups.save(
        new GuardianLookup(LOOKED_UP_MINOR_HOLDER, LATEST, NOW, UUID.randomUUID(), Set.of()));
  }

  @Test
  void backfillsEveryMinorThirdPillarHolderNotLookedUpYetOnceAsTheConfiguredRequester() {
    minorAndAdultThirdPillarHolders();

    lookups(REQUESTER).backfillMinorThirdPillarHolders(REQUESTER);

    var inOrder = inOrder(service);
    inOrder.verify(service).lookUpChildOnce(REQUESTER, MINOR_HOLDER);
    inOrder.verify(service).lookUpChildOnce(REQUESTER, OTHER_MINOR_HOLDER);
    for (String other : new String[] {LOOKED_UP_MINOR_HOLDER, ADULT_HOLDER}) {
      verify(service, never()).lookUpChildOnce(anyString(), eq(other));
    }
  }

  @Test
  void backfillsNoMinorWhoHoldsOnlySecondPillarUnits() {
    holder(MINOR_IN_THE_SECOND_PILLAR_ONLY, LATEST);

    assertThat(lookups(REQUESTER).backfillCandidates()).isEmpty();
  }

  @Test
  void stopsTheBackfillAtTheFirstRejectionAndLogsItOnceWithoutCodes(CapturedOutput output) {
    minorAndAdultThirdPillarHolders();
    given(service.lookUpChildOnce(REQUESTER, MINOR_HOLDER))
        .willThrow(
            new PopulationRegisterException(
                "rejected",
                HttpClientErrorException.create(
                    FORBIDDEN,
                    "Forbidden",
                    new HttpHeaders(),
                    "{\"code\": \"FORBIDDEN\", \"traceId\": \"abc123\"}".getBytes(UTF_8),
                    UTF_8)));

    lookups(REQUESTER).backfillMinorThirdPillarHolders(REQUESTER);

    verify(service, never()).lookUpChildOnce(anyString(), eq(OTHER_MINOR_HOLDER));
    assertThat(output.getAll().split("Guardian lookup backfill stopped", -1)).hasSize(2);
    assertThat(output.getAll())
        .contains("position=1, error=PopulationRegisterException, status=403, code=FORBIDDEN")
        .contains("Finished guardian lookup backfill: lookedUp=0, remaining=2")
        .doesNotContain(MINOR_HOLDER, OTHER_MINOR_HOLDER, REQUESTER);
  }

  @Test
  void stopsTheBackfillWhenThePopulationRegisterIsUnavailable(CapturedOutput output) {
    minorAndAdultThirdPillarHolders();
    given(service.lookUpChildOnce(REQUESTER, MINOR_HOLDER))
        .willThrow(
            new PopulationRegisterUnavailable(
                "unavailable",
                HttpServerErrorException.create(
                    SERVICE_UNAVAILABLE, "Unavailable", new HttpHeaders(), new byte[0], UTF_8)));

    lookups(REQUESTER).backfillMinorThirdPillarHolders(REQUESTER);

    verify(service, never()).lookUpChildOnce(anyString(), eq(OTHER_MINOR_HOLDER));
    assertThat(output.getAll())
        .contains("position=1, error=PopulationRegisterUnavailable, status=503")
        .doesNotContain(MINOR_HOLDER, OTHER_MINOR_HOLDER, REQUESTER);
  }

  @Test
  void resumesTheBackfillAtTheChildItStoppedAtOnceTheRegisterAnswersAgain() {
    minorAndAdultThirdPillarHolders();
    given(service.lookUpChildOnce(REQUESTER, OTHER_MINOR_HOLDER))
        .willThrow(new PopulationRegisterUnavailable("unavailable", new RuntimeException()));
    lookups(REQUESTER).backfillMinorThirdPillarHolders(REQUESTER);
    guardianLookups.save(
        new GuardianLookup(MINOR_HOLDER, LATEST, NOW, UUID.randomUUID(), Set.of()));

    assertThat(lookups(REQUESTER).backfillCandidates()).containsExactly(OTHER_MINOR_HOLDER);
  }

  @Test
  void backfillsUntilTheLastDayOf2026AndStopsByItselfAfterIt() {
    minorAndAdultThirdPillarHolders();
    Instant lastDay = Instant.parse("2026-12-31T23:59:59Z");

    lookupsAt(lastDay).backfillMinorThirdPillarHolders(REQUESTER);
    lookupsAt(lastDay.plusSeconds(1)).backfillMinorThirdPillarHolders(REQUESTER);

    verify(service).lookUpChildOnce(REQUESTER, MINOR_HOLDER);
    verify(service).lookUpChildOnce(REQUESTER, OTHER_MINOR_HOLDER);
    assertThat(AutomaticGuardianLookups.LAST_BACKFILL_DAY_AT_THE_2026_POPULATION_REGISTER_PRICE)
        .isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void backfillsNoMoreThanAThousandChildrenARun() {
    minorAndAdultThirdPillarHolders();
    assertThat(AutomaticGuardianLookups.MAX_BACKFILL_PER_RUN).isEqualTo(1000);
    assertThat(lookups(REQUESTER).backfillCandidates())
        .isEqualTo(List.of(MINOR_HOLDER, OTHER_MINOR_HOLDER));
  }

  @Test
  void looksUpNewHoldersBeforeBackfillingAtTheSnapshot() {
    holder(NEW_MINOR, LATEST);
    thirdPillarHolder(OTHER_MINOR_HOLDER, EARLIER);
    thirdPillarHolder(OTHER_MINOR_HOLDER, LATEST);

    lookups(REQUESTER).onUnitOwnerSnapshot();

    var inOrder = inOrder(service);
    inOrder.verify(service).lookUpChildOnce(REQUESTER, NEW_MINOR);
    inOrder.verify(service).lookUpChildOnce(REQUESTER, OTHER_MINOR_HOLDER);
  }

  private AutomaticGuardianLookups lookups(String requester) {
    return new AutomaticGuardianLookups(
        service,
        new AutomaticGuardianLookupCandidates(database, clock),
        new ThirdPillarAnalytics(transactions, unitOwners),
        guardianLookups,
        clock,
        requester,
        Duration.ZERO);
  }

  private AutomaticGuardianLookups lookupsAt(Instant now) {
    Clock at = Clock.fixed(now, ZoneOffset.UTC);
    return new AutomaticGuardianLookups(
        service,
        new AutomaticGuardianLookupCandidates(database, at),
        new ThirdPillarAnalytics(transactions, unitOwners),
        guardianLookups,
        at,
        REQUESTER,
        Duration.ZERO);
  }

  private void thirdPillarHolder(String code, LocalDate snapshot) {
    unitOwners.save(
        UnitOwner.builder()
            .personalId(code)
            .snapshotDate(snapshot)
            .balances(
                List.of(
                    UnitOwnerBalanceEmbeddable.builder()
                        .securityShortName("TUV100")
                        .amount(BigDecimal.ONE)
                        .build()))
            .dateCreated(LocalDateTime.of(2026, 11, 19, 0, 0))
            .build());
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
