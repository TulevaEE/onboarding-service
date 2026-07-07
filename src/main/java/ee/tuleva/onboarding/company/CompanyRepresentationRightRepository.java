package ee.tuleva.onboarding.company;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepresentationRightRepository
    extends JpaRepository<CompanyRepresentationRight, UUID> {

  List<CompanyRepresentationRight> findByCompanyId(UUID companyId);

  @Modifying(flushAutomatically = true)
  @Query(
      "delete from CompanyRepresentationRight representationRight where representationRight.companyId = :companyId")
  void deleteByCompanyId(UUID companyId);
}
