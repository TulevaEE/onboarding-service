package ee.tuleva.onboarding.company;

import ee.tuleva.onboarding.party.PartyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPartyRepository extends JpaRepository<CompanyParty, UUID> {

  List<CompanyParty> findByPartyCodeAndPartyTypeAndRelationshipType(
      String partyCode, PartyId.Type partyType, RelationshipType relationshipType);

  boolean existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
      String partyCode, PartyId.Type partyType, UUID companyId, RelationshipType relationshipType);
}
