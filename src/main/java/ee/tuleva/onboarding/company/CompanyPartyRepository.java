package ee.tuleva.onboarding.company;

import ee.tuleva.onboarding.party.Party;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPartyRepository extends JpaRepository<CompanyParty, UUID> {

  List<CompanyParty> findByPartyCodeAndPartyTypeAndRelationshipType(
      String partyCode, Party.Type partyType, RelationshipType relationshipType);

  boolean existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
      String partyCode, Party.Type partyType, UUID companyId, RelationshipType relationshipType);
}
