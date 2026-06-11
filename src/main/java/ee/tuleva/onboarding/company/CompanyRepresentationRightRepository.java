package ee.tuleva.onboarding.company;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepresentationRightRepository
    extends JpaRepository<CompanyRepresentationRight, UUID> {

  List<CompanyRepresentationRight> findByCompanyId(UUID companyId);

  void deleteByCompanyId(UUID companyId);
}
