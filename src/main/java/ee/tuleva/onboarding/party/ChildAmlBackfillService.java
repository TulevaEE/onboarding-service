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
import static ee.tuleva.onboarding.party.ChildOnboardingService.CUSTODY_MAX_AGE;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One-off backfill for children onboarded before custody re-verification, citizenship capture and
 * sanction/PEP screening were part of child onboarding. Not transactional at the batch level on
 * purpose: each child's checks commit independently, so a mid-batch failure keeps the rows already
 * written.
 */
@Slf4j
@Service
@RequiredArgsConstructor
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
            .collect(groupingBy(ParentChildLink::getChildPersonalCode, TreeMap::new, toList()));

    log.info("Starting child AML backfill: dryRun={}, children={}", dryRun, linksByChild.size());

    List<ChildResult> results = new ArrayList<>();
    linksByChild.forEach(
        (childCode, links) ->
            results.add(processChild(requesterPersonalCode, childCode, links, today, dryRun)));

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
    boolean hasUser = userRepository.findByPersonalCode(childCode).isPresent();
    try {
      if (hasCitizenshipOnFile(childCode)) {
        return ChildResult.skipped(childCode, ALREADY_BACKFILLED, hasUser);
      }
      if (!PersonalCode.isMinor(childCode, today)) {
        return ChildResult.skipped(childCode, TURNED_ADULT, hasUser);
      }

      List<String> legalRepresentatives =
          links.stream()
              .filter(link -> link.getRelationshipType() == LEGAL_REPRESENTATIVE)
              .map(ParentChildLink::getParentPersonalCode)
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
    CustodyVerification verification = null;
    for (String parentCode : parentCodes) {
      verification =
          custodyVerificationService.verify(
              requesterPersonalCode, parentCode, childCode, CUSTODY_MAX_AGE);
      if (verification.isVerified()) {
        break;
      }
    }
    amlService.addCustodyRightCheck(
        childCode, verification.isVerified(), verification.evidenceWithCitizenship());

    PopulationRegisterPerson child = verification.child();
    ScreeningStatus screeningStatus;
    if (verification.outcome() == CHILD_NOT_ALIVE) {
      screeningStatus = SKIPPED;
    } else {
      if (child == null) {
        child =
            populationRegisterClient
                .fetchPerson(requesterPersonalCode, childCode, CUSTODY_MAX_AGE)
                .data();
      }
      screeningStatus = screen(child);
    }
    return new ChildResult(
        childCode,
        verification.isVerified() ? BACKFILLED : CUSTODY_NOT_VERIFIED,
        verification.outcome().name(),
        child == null ? null : child.citizenship(),
        screeningStatus,
        hasUser,
        null);
  }

  private ChildResult screenGuardianWard(
      String requesterPersonalCode, String childCode, boolean hasUser) {
    PopulationRegisterPerson child =
        populationRegisterClient
            .fetchPerson(requesterPersonalCode, childCode, CUSTODY_MAX_AGE)
            .data();
    return new ChildResult(
        childCode, GUARDIAN_LINK, null, child.citizenship(), screen(child), hasUser, null);
  }

  private ScreeningStatus screen(PopulationRegisterPerson child) {
    amlService.addSanctionAndPepCheckIfMissing(
        new PersonImpl(child.personalCode(), child.firstName(), child.lastName()),
        new Country(child.citizenship()));
    // Screening is fail-open and returns an empty list both when it failed and when the result was
    // deduplicated, so success can only be confirmed by the row's existence.
    boolean screened =
        amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            child.personalCode(), SANCTION, aYearAgo());
    return screened ? SCREENED : SCREENING_FAILED;
  }

  private boolean hasCitizenshipOnFile(String childCode) {
    // Deliberately not year-scoped: any custody-right row that already carries citizenship means
    // the child went through the current onboarding checks, however long ago.
    return amlCheckRepository.findAllByPersonalCodeAndType(childCode, CUSTODY_RIGHT).stream()
        .anyMatch(
            check -> check.getMetadata() != null && check.getMetadata().containsKey("citizenship"));
  }
}
