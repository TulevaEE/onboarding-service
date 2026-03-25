package ee.tuleva.onboarding.company;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPartyRepository extends JpaRepository<CompanyParty, UUID> {

  List<CompanyParty> findByPartyCodeAndPartyTypeAndRelationshipType(
      String partyCode, PartyType partyType, RelationshipType relationshipType);

  boolean existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
      String partyCode, PartyType partyType, UUID companyId, RelationshipType relationshipType);
}
