package ee.tuleva.onboarding.kyb;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KybCheckOverrideRepository extends JpaRepository<KybCheckOverride, UUID> {

  List<KybCheckOverride> findByRegistryCode(String registryCode);

  Optional<KybCheckOverride> findByRegistryCodeAndCheckType(
      String registryCode, KybCheckType checkType);
}
