package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParentChildLinkService {

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final Clock clock;

  public List<String> findActivelyRepresentedChildCodes(String parentPersonalCode) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, ACTIVE, today())
        .stream()
        .map(ParentChildLink::getChildPersonalCode)
        .toList();
  }

  public boolean isActiveRepresentation(String parentPersonalCode, String childPersonalCode) {
    return parentChildLinkRepository
        .existsByParentPersonalCodeAndChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
            parentPersonalCode, childPersonalCode, ACTIVE, today());
  }

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
