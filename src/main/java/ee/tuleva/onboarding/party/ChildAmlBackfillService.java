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
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult.ChildResult;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult.ScreeningStatus;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
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
        parentChildLinkRepository.findAll().stream()
            .filter(
                link ->
                    link.getStatus() == ACTIVE
                        && !link.isSuspended()
                        && link.getValidUntil().isAfter(today))
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
      hasUser = userRepository.findByPersonalCode(childCode).isPresent();
      if (hasCitizenshipOnFile(childCode) && hasBeenScreened(childCode)) {
        return ChildResult.skipped(childCode, ALREADY_BACKFILLED, hasUser);
      }
      if (!PersonalCode.isMinor(childCode, today)) {
        return ChildResult.skipped(childCode, TURNED_ADULT, hasUser);
      }

      List<String> legalRepresentatives =
          links.stream()
              .filter(link -> link.getRelationshipType() == LEGAL_REPRESENTATIVE)
              .map(link -> link.getParentPersonalCode().strip())
              .distinct()
              .sorted()
              .toList();

      if (legalRepresentatives.isEmpty()) {
        // Court-appointed guardians are not parental custody, so the register's custody query
        // would report a false NO_CUSTODY — screen and report for manual review instead.
        return dryRun
            ? ChildResult.skipped(childCode, GUARDIAN_LINK, hasUser)
            : screenGuardianWard(requesterPersonalCode, childCode, hasUser);
      }
      return dryRun
          ? ChildResult.skipped(childCode, WOULD_PROCESS, hasUser)
          : reverify(requesterPersonalCode, childCode, legalRepresentatives, hasUser);
    } catch (RuntimeException e) {
      log.error("Child AML backfill failed for child: childCode={}", childCode, e);
      return ChildResult.error(childCode, hasUser, e);
    }
  }

  private ChildResult reverify(
      String requesterPersonalCode, String childCode, List<String> parentCodes, boolean hasUser) {
    CustodyVerification verification =
        verifyAgainstAnyParent(requesterPersonalCode, childCode, parentCodes);
    amlService.addCustodyRightCheck(
        childCode, verification.isVerified(), verification.evidenceWithCitizenship());

    PopulationRegisterPerson child = verification.child();
    ScreeningStatus screeningStatus;
    if (verification.outcome() == CHILD_NOT_ALIVE) {
      screeningStatus = SKIPPED;
    } else {
      if (child == null) {
        child = populationRegisterClient.fetchPersonFresh(requesterPersonalCode, childCode).data();
      }
      screeningStatus = screen(child);
    }
    return new ChildResult(
        childCode,
        verification.isVerified() ? BACKFILLED : CUSTODY_NOT_VERIFIED,
        verification.outcome(),
        child == null ? null : child.citizenship(),
        screeningStatus,
        hasUser,
        null);
  }

  private CustodyVerification verifyAgainstAnyParent(
      String requesterPersonalCode, String childCode, List<String> parentCodes) {
    CustodyVerification verification =
        custodyVerificationService.verifyFresh(
            requesterPersonalCode, parentCodes.getFirst(), childCode);
    for (String parentCode : parentCodes.subList(1, parentCodes.size())) {
      if (verification.isVerified()) {
        break;
      }
      verification =
          custodyVerificationService.verifyFresh(requesterPersonalCode, parentCode, childCode);
    }
    return verification;
  }

  private ChildResult screenGuardianWard(
      String requesterPersonalCode, String childCode, boolean hasUser) {
    PopulationRegisterPerson child =
        populationRegisterClient.fetchPersonFresh(requesterPersonalCode, childCode).data();
    return new ChildResult(
        childCode, GUARDIAN_LINK, null, child.citizenship(), screen(child), hasUser, null);
  }

  private ScreeningStatus screen(PopulationRegisterPerson child) {
    amlService.addSanctionAndPepCheckIfMissing(
        new PersonImpl(child.personalCode(), child.firstName(), child.lastName()),
        new Country(child.citizenship()));
    // Screening is fail-open and returns an empty list both when it failed and when the result was
    // deduplicated, so success can only be confirmed by the row's existence.
    return hasBeenScreened(child.personalCode()) ? SCREENED : SCREENING_FAILED;
  }

  private boolean hasBeenScreened(String childCode) {
    return amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
        childCode, SANCTION, aYearAgo());
  }

  private boolean hasCitizenshipOnFile(String childCode) {
    // Deliberately not year-scoped: any custody-right row that already carries citizenship means
    // the child went through the current onboarding checks, however long ago.
    return amlCheckRepository.findAllByPersonalCodeAndType(childCode, CUSTODY_RIGHT).stream()
        .anyMatch(
            check -> check.getMetadata() != null && check.getMetadata().containsKey("citizenship"));
  }
}
