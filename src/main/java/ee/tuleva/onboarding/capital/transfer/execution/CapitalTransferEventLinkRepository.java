package ee.tuleva.onboarding.capital.transfer.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CapitalTransferEventLinkRepository
    extends JpaRepository<CapitalTransferEventLink, Long> {}
