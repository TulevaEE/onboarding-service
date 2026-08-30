package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.aml.AmlCheckType.CUSTODY_RIGHT;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.ALREADY_BACKFILLED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.BACKFILLED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.CUSTODY_NOT_VERIFIED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.GUARDIAN_LINK;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.TURNED_ADULT;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome.WOULD_PROCESS;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.SCREENED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.SCREENING_FAILED;
import static ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus.SKIPPED;
import static ee.tuleva.onboarding.party.CustodyVerification.CITIZENSHIPS;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult.ChildResult;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult.Outcome;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class ChildAmlBackfillService {

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final CustodyVerificationService custodyVerificationService;
  private final PopulationRegisterClient populationRegisterClient;
  private final AmlService amlService;
  private final AmlCheckRepository amlCheckRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public ChildAmlBackfillResult backfill(String requesterPersonalCode, boolean dryRun) {
    LocalDate today = LocalDate.now(clock);
    Map<String, List<ParentChildLink>> linksByChild =
        parentChildLinkRepository
            .findByStatusAndSuspendedAtIsNullAndValidUntilAfter(ACTIVE, today)
            .stream()
            .collect(
                groupingBy(link -> link.getChildPersonalCode().strip(), TreeMap::new, toList()));

    log.info("Starting child AML backfill: dryRun={}, children={}", dryRun, linksByChild.size());

    List<ChildResult> results =
        linksByChild.entrySet().stream()
            .map(
                entry ->
                    processChild(
                        requesterPersonalCode, entry.getKey(), entry.getValue(), today, dryRun))
            .toList();

    ChildAmlBackfillResult result = ChildAmlBackfillResult.of(dryRun, results);
    log.info(
        "Finished child AML backfill: dryRun={}, total={}, counts={}",
        dryRun,
        result.total(),
        result.counts());
    return result;
  }

  private ChildResult processChild(
      String requesterPersonalCode,
      String childCode,
      List<ParentChildLink> links,
      LocalDate today,
      boolean dryRun) {
    boolean hasUser = false;
    try {
      hasUser = userRepository.existsByPersonalCode(childCode);
      if (hasRecordedEveryCitizenship(childCode) && hasRecentSanctionRow(childCode)) {
        return ChildResult.reported(childCode, ALREADY_BACKFILLED, hasUser);
      }
      if (!PersonalCode.isMinor(childCode, today)) {
        return ChildResult.reported(childCode, TURNED_ADULT, hasUser);
      }

      List<String> legalRepresentatives = legalRepresentativesIn(links);
      if (legalRepresentatives.isEmpty()) {
        return dryRun
            ? ChildResult.reported(childCode, GUARDIAN_LINK, hasUser)
            : screenWardWithoutCustodyQuery(requesterPersonalCode, childCode, hasUser);
      }
      return dryRun
          ? ChildResult.reported(childCode, WOULD_PROCESS, hasUser)
          : verifyCustodyAndScreen(requesterPersonalCode, childCode, legalRepresentatives, hasUser);
    } catch (RuntimeException e) {
      log.error("Child AML backfill failed for child: childCode={}", childCode, e);
      return ChildResult.error(childCode, hasUser, e);
    }
  }

  private List<String> legalRepresentativesIn(List<ParentChildLink> links) {
    return links.stream()
        .filter(link -> link.getRelationshipType() == LEGAL_REPRESENTATIVE)
        .map(link -> link.getParentPersonalCode().strip())
        .distinct()
        .sorted()
        .toList();
  }

  private ChildResult verifyCustodyAndScreen(
      String requesterPersonalCode, String childCode, List<String> parentCodes, boolean hasUser) {
    CustodyVerification verification =
        verifyAgainstAnyParent(requesterPersonalCode, childCode, parentCodes);
    amlService.addCustodyRightCheck(
        childCode, verification.isVerified(), verification.evidenceWithCitizenships());

    Outcome outcome = verification.isVerified() ? BACKFILLED : CUSTODY_NOT_VERIFIED;
    if (verification.outcome() == CHILD_NOT_ALIVE) {
      return new ChildResult(
          childCode, outcome, verification.outcome(), null, SKIPPED, hasUser, null);
    }
    PopulationRegisterPerson child =
        Optional.ofNullable(verification.child())
            .orElseGet(() -> fetchChild(requesterPersonalCode, childCode));
    return new ChildResult(
        childCode,
        outcome,
        verification.outcome(),
        child.citizenship(),
        screenAndConfirmBySanctionRow(child),
        hasUser,
        null);
  }

  private CustodyVerification verifyAgainstAnyParent(
      String requesterPersonalCode, String childCode, List<String> parentCodes) {
    @Nullable CustodyVerification lastAttempt = null;
    for (String parentCode : parentCodes) {
      lastAttempt =
          custodyVerificationService.verifyFresh(requesterPersonalCode, parentCode, childCode);
      if (lastAttempt.isVerified()) {
        return lastAttempt;
      }
    }
    return requireNonNull(lastAttempt);
  }

  private ChildResult screenWardWithoutCustodyQuery(
      String requesterPersonalCode, String childCode, boolean hasUser) {
    PopulationRegisterPerson child = fetchChild(requesterPersonalCode, childCode);
    return new ChildResult(
        childCode,
        GUARDIAN_LINK,
        null,
        child.citizenship(),
        screenAndConfirmBySanctionRow(child),
        hasUser,
        null);
  }

  private PopulationRegisterPerson fetchChild(String requesterPersonalCode, String childCode) {
    return populationRegisterClient.fetchPersonFresh(requesterPersonalCode, childCode).data();
  }

  private ScreeningStatus screenAndConfirmBySanctionRow(PopulationRegisterPerson child) {
    amlService.addSanctionAndPepCheckIfMissing(
        new PersonImpl(child.personalCode(), child.firstName(), child.lastName()),
        Countries.of(child.citizenships().toArray(new String[0])));
    return hasRecentSanctionRow(child.personalCode()) ? SCREENED : SCREENING_FAILED;
  }

  private boolean hasRecentSanctionRow(String childCode) {
    return amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
        childCode, SANCTION, aYearAgo());
  }

  private boolean hasRecordedEveryCitizenship(String childCode) {
    return amlCheckRepository.findAllByPersonalCodeAndType(childCode, CUSTODY_RIGHT).stream()
        .anyMatch(check -> check.hasMetadata(CITIZENSHIPS));
  }
}
