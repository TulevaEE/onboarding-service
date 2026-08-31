package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;

import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParentChildLinkService implements ChildRepresentations {

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final Clock clock;

  @Override
  public List<String> findActivelyRepresentedChildCodes(String parentPersonalCode) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, ACTIVE, today())
        .stream()
        .map(ParentChildLink::getChildPersonalCode)
        .toList();
  }

  public boolean isRepresentation(
      String parentPersonalCode, String childPersonalCode, Set<ParentChildLinkStatus> statuses) {
    return parentChildLinkRepository
        .existsByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, childPersonalCode, statuses, today());
  }

  // The parent acting *as* the child: requires the parent to have cleared their own KYC.
  @Override
  public boolean isActiveRepresentation(String parentPersonalCode, String childPersonalCode) {
    return isRepresentation(parentPersonalCode, childPersonalCode, Set.of(ACTIVE));
  }

  // AML screening scope, not an authorization check: suspended and PENDING_KYC links count,
  // because suspension does not cleanse the guardian-risk association (mirrors check_24).
  public List<String> findGuardianCodes(String childPersonalCode) {
    return parentChildLinkRepository
        .findByChildPersonalCodeAndValidUntilAfter(childPersonalCode, today())
        .stream()
        .map(ParentChildLink::getParentPersonalCode)
        .distinct()
        .toList();
  }

  @Override
  public List<String> findPendingChildCodes(String parentPersonalCode) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, PENDING_KYC, today())
        .stream()
        .map(ParentChildLink::getChildPersonalCode)
        .toList();
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
