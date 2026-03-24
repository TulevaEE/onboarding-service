package ee.tuleva.onboarding.company;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

  Optional<Company> findByRegistryCode(String registryCode);
}
