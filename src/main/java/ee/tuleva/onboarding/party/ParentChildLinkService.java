package ee.tuleva.onboarding.party;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParentChildLinkService {

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final Clock clock;

  public List<String> findActivelyRepresentedChildCodes(String parentPersonalCode) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(parentPersonalCode, today())
        .stream()
        .map(ParentChildLink::getChildPersonalCode)
        .toList();
  }

  public boolean isActiveRepresentation(String parentPersonalCode, String childPersonalCode) {
    return parentChildLinkRepository
        .existsByParentPersonalCodeAndChildPersonalCodeAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, childPersonalCode, today());
  }

  // The parent's still-valid PENDING_KYC links (the "join the account" account-selector entries).
  // These grant no access; selecting one starts the co-parent's own onboarding/KYC join flow.
  public List<ParentChildLink> findPendingChildren(String parentPersonalCode) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, ParentChildLinkStatus.PENDING_KYC, today());
  }

  // Resolves a pending link's opaque id to the child's personal code, SCOPED to the authenticated
  // parent + status PENDING_KYC. The id is URL-safety, never authorization: a parent can only
  // resolve their own pending links. Used by the frontend join flow before running onboarding.
  public String resolvePendingChildCode(String parentPersonalCode, UUID pendingLinkId) {
    return parentChildLinkRepository
        .findById(pendingLinkId)
        .filter(link -> link.getParentPersonalCode().equals(parentPersonalCode))
        .filter(ParentChildLink::isPending)
        .map(ParentChildLink::getChildPersonalCode)
        .orElseThrow(() -> new PendingChildLinkNotFoundException(parentPersonalCode, pendingLinkId));
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
