package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParentChildLinkService implements ChildRepresentations {

  private static final Comparator<ParentChildLink> CANONICAL_LINK =
      comparing((ParentChildLink link) -> link.getStatus() == ACTIVE ? 0 : 1)
          .thenComparing(ParentChildLink::getId, nullsLast(naturalOrder()));

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final Clock clock;

  @Override
  public Map<String, UUID> findActivelyRepresentedChildren(String parentPersonalCode) {
    var children = new LinkedHashMap<String, UUID>();
    parentChildLinkRepository
        .findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, ACTIVE, today())
        .stream()
        .sorted(CANONICAL_LINK)
        .forEach(link -> children.putIfAbsent(link.getChildPersonalCode(), link.getId()));
    return children;
  }

  // One canonical link per parent and child, so a role listed in /v1/me/roles and a deep link
  // built for the same child always name the same one.
  public Optional<UUID> findRepresentation(
      String parentPersonalCode, String childPersonalCode, Set<ParentChildLinkStatus> statuses) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndStatusInAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, childPersonalCode, statuses, today())
        .stream()
        .min(CANONICAL_LINK)
        .map(ParentChildLink::getId);
  }

  // The parent acting *as* the child: requires the parent to have cleared their own KYC.
  @Override
  public boolean isActiveRepresentation(String parentPersonalCode, String childPersonalCode) {
    return findRepresentation(parentPersonalCode, childPersonalCode, Set.of(ACTIVE)).isPresent();
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
