package ee.tuleva.onboarding.company;

import ee.tuleva.onboarding.party.PartyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyPartyRepository extends JpaRepository<CompanyParty, UUID> {

  List<CompanyParty> findByPartyCodeAndPartyTypeAndRelationshipType(
      String partyCode, PartyId.Type partyType, RelationshipType relationshipType);

  boolean existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
      String partyCode, PartyId.Type partyType, UUID companyId, RelationshipType relationshipType);

  @Modifying
  @Query("DELETE FROM CompanyParty p WHERE p.companyId = :companyId")
  void deleteByCompanyId(@Param("companyId") UUID companyId);
}
