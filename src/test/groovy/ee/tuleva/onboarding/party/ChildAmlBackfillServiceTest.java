package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.aml.AmlCheckType.CUSTODY_RIGHT;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.ALREADY_BACKFILLED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.BACKFILLED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.CUSTODY_NOT_VERIFIED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.ERROR;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.GUARDIAN_LINK;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.TURNED_ADULT;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.WOULD_PROCESS;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.NOT_ATTEMPTED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.SCREENED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.SCREENING_FAILED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.SKIPPED;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult.ChildResult;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.populationregister.PopulationRegisterResult;
import ee.tuleva.onboarding.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildAmlBackfillServiceTest {

  private static final String OPS = "38001010002";
  private static final String PARENT = "38812121215";
  private static final String CO_PARENT = "38002020008";
  private static final String CHILD = "61506150006";
  private static final String OTHER_CHILD = "61001010000";
  private static final String ADULT = "39912310015";

  @Mock private ParentChildLinkRepository parentChildLinkRepository;
  @Mock private CustodyVerificationService custodyVerificationService;
  @Mock private PopulationRegisterClient populationRegisterClient;
  @Mock private AmlService amlService;
  @Mock private SanctionAndPepScreener sanctionAndPepScreener;
  @Mock private AmlCheckRepository amlCheckRepository;
  @Mock private UserRepository userRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);

  private ChildAmlBackfillService service;

  @BeforeEach
  void setUp() {
    service =
        new ChildAmlBackfillService(
            parentChildLinkRepository,
            custodyVerificationService,
            populationRegisterClient,
            amlService,
            sanctionAndPepScreener,
            amlCheckRepository,
            userRepository,
            clock);
  }

  private final PopulationRegisterPerson child =
      new PopulationRegisterPerson(
          CHILD, "MARI", "MAASIKAS", LocalDate.of(2015, 6, 15), ALIVE, "EE", List.of("EE"));

  private static ParentChildLink link(String parent, String child, RepresentationType type) {
    return ParentChildLink.builder()
        .parentPersonalCode(parent)
        .childPersonalCode(child)
        .relationshipType(type)
        .status(ACTIVE)
        .validUntil(LocalDate.of(2033, 6, 15))
        .build();
  }

  private void givenLinks(ParentChildLink... links) {
    given(
            parentChildLinkRepository.findByStatusAndSuspendedAtIsNullAndValidUntilAfter(
                ACTIVE, LocalDate.of(2026, 7, 29)))
        .willReturn(List.of(links));
  }

  private void givenSanctionRowExists(boolean exists) {
    given(
            amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
                eq(CHILD), eq(SANCTION), any(Instant.class)))
        .willReturn(exists);
  }

  private static AmlCheck custodyCheck(Map<String, Object> metadata) {
    return AmlCheck.builder()
        .personalCode(CHILD)
        .type(CUSTODY_RIGHT)
        .success(true)
        .metadata(metadata)
        .build();
  }

  @Test
  void verifiedCustody_writesCustodyCheckWithCitizenshipAndScreens() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    var evidence = Map.<String, Object>of("outcome", "OK", "childPersonalCode", CHILD);
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, evidence));
    given(userRepository.existsByPersonalCode(CHILD)).willReturn(true);
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    var evidenceWithCitizenship = new LinkedHashMap<String, Object>(evidence);
    evidenceWithCitizenship.put("citizenship", "EE");
    evidenceWithCitizenship.put("citizenships", List.of("EE"));
    verify(amlService).addCustodyRightCheck(CHILD, true, evidenceWithCitizenship);
    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(
            new PersonImpl(CHILD, "MARI", "MAASIKAS"), Countries.of("EE"));
    assertThat(result.children())
        .containsExactly(new ChildResult(CHILD, BACKFILLED, OK, "EE", SCREENED, true, null));
  }

  @Test
  void childWithEveryCitizenshipAndScreeningOnFile_isSkippedWithoutRegisterCalls() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(amlCheckRepository.findAllByPersonalCodeAndType(CHILD, CUSTODY_RIGHT))
        .willReturn(
            List.of(
                custodyCheck(
                    Map.of("outcome", "OK", "citizenship", "EE", "citizenships", List.of("EE")))));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children())
        .containsExactly(
            new ChildResult(CHILD, ALREADY_BACKFILLED, null, null, NOT_ATTEMPTED, false, null));
    verifyNoInteractions(
        custodyVerificationService, populationRegisterClient, amlService, sanctionAndPepScreener);
  }

  @Test
  void childWithEveryCitizenshipButFailedScreening_isRetriedOnRerun() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(amlCheckRepository.findAllByPersonalCodeAndType(CHILD, CUSTODY_RIGHT))
        .willReturn(
            List.of(
                custodyCheck(
                    Map.of("outcome", "OK", "citizenship", "EE", "citizenships", List.of("EE")))));
    given(
            amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
                eq(CHILD), eq(SANCTION), any(Instant.class)))
        .willReturn(false, true);
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(
            new PersonImpl(CHILD, "MARI", "MAASIKAS"), Countries.of("EE"));
    assertThat(result.children())
        .extracting(ChildResult::outcome, ChildResult::screeningStatus)
        .containsExactly(tuple(BACKFILLED, SCREENED));
  }

  @Test
  void childWithCustodyCheckButNoCitizenship_isStillReverified() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(amlCheckRepository.findAllByPersonalCodeAndType(CHILD, CUSTODY_RIGHT))
        .willReturn(List.of(custodyCheck(Map.of("outcome", "OK"))));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children()).extracting(ChildResult::outcome).containsExactly(BACKFILLED);
  }

  @Test
  void unverifiedCustody_writesFailedCheckStillScreensAndDoesNotTouchTheLink() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    var evidence = Map.<String, Object>of("outcome", "NO_CUSTODY", "childPersonalCode", CHILD);
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(CustodyVerification.notVerified(NO_CUSTODY, evidence));
    given(populationRegisterClient.fetchPersonFresh(OPS, CHILD))
        .willReturn(new PopulationRegisterResult<>(child, UUID.randomUUID()));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    verify(amlService).addCustodyRightCheck(CHILD, false, evidence);
    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(
            new PersonImpl(CHILD, "MARI", "MAASIKAS"), Countries.of("EE"));
    verify(parentChildLinkRepository, never()).save(any());
    assertThat(result.children())
        .containsExactly(
            new ChildResult(CHILD, CUSTODY_NOT_VERIFIED, NO_CUSTODY, "EE", SCREENED, false, null));
  }

  @Test
  void childNotAlive_skipsScreening() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(
            CustodyVerification.notVerified(CHILD_NOT_ALIVE, Map.of("outcome", "CHILD_NOT_ALIVE")));

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    verify(sanctionAndPepScreener, never()).addSanctionAndPepCheckIfMissing(any(), any());
    verify(populationRegisterClient, never()).fetchPersonFresh(any(), any());
    assertThat(result.children()).extracting(ChildResult::screeningStatus).containsExactly(SKIPPED);
  }

  @Test
  void triesEveryParentUntilOneVerifiesAndWritesOnlyThatCheck() {
    givenLinks(
        link(CO_PARENT, CHILD, LEGAL_REPRESENTATIVE), link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(custodyVerificationService.verifyFresh(OPS, CO_PARENT, CHILD))
        .willReturn(CustodyVerification.notVerified(NO_CUSTODY, Map.of("outcome", "NO_CUSTODY")));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    verify(amlService).addCustodyRightCheck(eq(CHILD), eq(true), any());
    verify(amlService, never()).addCustodyRightCheck(eq(CHILD), eq(false), any());
    assertThat(result.children()).extracting(ChildResult::outcome).containsExactly(BACKFILLED);
  }

  @Test
  void guardianOnlyChild_isScreenedButGetsNoCustodyCheck() {
    givenLinks(link(PARENT, CHILD, GUARDIAN));
    given(populationRegisterClient.fetchPersonFresh(OPS, CHILD))
        .willReturn(new PopulationRegisterResult<>(child, UUID.randomUUID()));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    verify(amlService, never()).addCustodyRightCheck(any(), anyBoolean(), any());
    verify(sanctionAndPepScreener)
        .addSanctionAndPepCheckIfMissing(
            new PersonImpl(CHILD, "MARI", "MAASIKAS"), Countries.of("EE"));
    verifyNoInteractions(custodyVerificationService);
    assertThat(result.children())
        .containsExactly(new ChildResult(CHILD, GUARDIAN_LINK, null, "EE", SCREENED, false, null));
  }

  @Test
  void childWhoTurnedAdult_isReportedWithoutRegisterCalls() {
    givenLinks(link(PARENT, ADULT, LEGAL_REPRESENTATIVE));

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children())
        .containsExactly(
            new ChildResult(ADULT, TURNED_ADULT, null, null, NOT_ATTEMPTED, false, null));
    verifyNoInteractions(
        custodyVerificationService, populationRegisterClient, amlService, sanctionAndPepScreener);
  }

  @Test
  void oneChildFailing_doesNotAbortTheOthers() {
    givenLinks(
        link(PARENT, OTHER_CHILD, LEGAL_REPRESENTATIVE), link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, OTHER_CHILD))
        .willThrow(new RuntimeException("register down"));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children())
        .extracting(ChildResult::childPersonalCode, ChildResult::outcome)
        .containsExactly(tuple(OTHER_CHILD, ERROR), tuple(CHILD, BACKFILLED));
    assertThat(result.children().getFirst().error()).isNotNull();
  }

  @Test
  void screeningThatWritesNoSanctionRow_isReportedAsFailed() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));
    givenSanctionRowExists(false);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children())
        .extracting(ChildResult::screeningStatus)
        .containsExactly(SCREENING_FAILED);
  }

  @Test
  void personalCodesWithWhitespace_areStrippedBeforeLookupsAndRegisterCalls() {
    givenLinks(link(" " + PARENT, CHILD + " ", LEGAL_REPRESENTATIVE));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    verify(amlCheckRepository).findAllByPersonalCodeAndType(CHILD, CUSTODY_RIGHT);
    verify(amlService).addCustodyRightCheck(eq(CHILD), eq(true), any());
    assertThat(result.children())
        .extracting(ChildResult::childPersonalCode, ChildResult::outcome)
        .containsExactly(tuple(CHILD, BACKFILLED));
  }

  @Test
  void dryRun_classifiesWithoutRegisterCallsOrWrites() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE), link(PARENT, OTHER_CHILD, GUARDIAN));

    ChildAmlBackfillResult result = service.backfill(OPS, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.children())
        .extracting(ChildResult::childPersonalCode, ChildResult::outcome)
        .containsExactly(tuple(OTHER_CHILD, GUARDIAN_LINK), tuple(CHILD, WOULD_PROCESS));
    verifyNoInteractions(
        custodyVerificationService, populationRegisterClient, amlService, sanctionAndPepScreener);
  }

  @Test
  void noActiveLinks_producesAnEmptyReport() {
    givenLinks();

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.total()).isZero();
    assertThat(result.children()).isEmpty();
    verifyNoInteractions(
        custodyVerificationService, populationRegisterClient, amlService, sanctionAndPepScreener);
  }

  @Test
  void childTheRegisterKnowsNoCitizenshipFor_isNotReverifiedOnEveryRun() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(amlCheckRepository.findAllByPersonalCodeAndType(CHILD, CUSTODY_RIGHT))
        .willReturn(List.of(custodyCheck(Map.of("outcome", "OK", "citizenships", List.of()))));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children())
        .containsExactly(
            new ChildResult(CHILD, ALREADY_BACKFILLED, null, null, NOT_ATTEMPTED, false, null));
    verifyNoInteractions(
        custodyVerificationService, populationRegisterClient, amlService, sanctionAndPepScreener);
  }

  @Test
  void childRecordedBeforeCitizenshipsWereCaptured_isReverifiedForTheMissingOnes() {
    givenLinks(link(PARENT, CHILD, LEGAL_REPRESENTATIVE));
    given(amlCheckRepository.findAllByPersonalCodeAndType(CHILD, CUSTODY_RIGHT))
        .willReturn(List.of(custodyCheck(Map.of("outcome", "OK", "citizenship", "EE"))));
    given(custodyVerificationService.verifyFresh(OPS, PARENT, CHILD))
        .willReturn(new CustodyVerification(OK, child, Map.of("outcome", "OK")));
    givenSanctionRowExists(true);

    ChildAmlBackfillResult result = service.backfill(OPS, false);

    assertThat(result.children()).extracting(ChildResult::outcome).containsExactly(BACKFILLED);
  }
}
