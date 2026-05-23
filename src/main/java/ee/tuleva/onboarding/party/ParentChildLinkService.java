package ee.tuleva.onboarding.party;

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
        .findByParentPersonalCodeAndValidUntilAfter(parentPersonalCode, today())
        .stream()
        .map(ParentChildLink::getChildPersonalCode)
        .toList();
  }

  public boolean represents(String parentPersonalCode, String childPersonalCode) {
    return parentChildLinkRepository
        .existsByParentPersonalCodeAndChildPersonalCodeAndValidUntilAfter(
            parentPersonalCode, childPersonalCode, today());
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
