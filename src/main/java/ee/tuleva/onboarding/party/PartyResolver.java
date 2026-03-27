package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartyResolver {

  private final UserRepository userRepository;
  private final CompanyRepository companyRepository;

  public Optional<Party> resolve(PartyId partyId) {
    return switch (partyId.type()) {
      case PERSON -> userRepository.findByPersonalCode(partyId.code()).map(Party.class::cast);
      case LEGAL_ENTITY ->
          companyRepository.findByRegistryCode(partyId.code()).map(Party.class::cast);
    };
  }
}
