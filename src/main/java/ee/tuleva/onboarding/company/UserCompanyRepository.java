package ee.tuleva.onboarding.company;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCompanyRepository extends JpaRepository<UserCompany, UUID> {

  List<UserCompany> findByUserIdAndRelationshipType(Long userId, RelationshipType relationshipType);

  boolean existsByUserIdAndCompanyIdAndRelationshipType(
      Long userId, UUID companyId, RelationshipType relationshipType);
}
