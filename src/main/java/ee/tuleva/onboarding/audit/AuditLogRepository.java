package ee.tuleva.onboarding.audit;

import org.springframework.data.repository.CrudRepository;

public interface AuditLogRepository extends CrudRepository<AuditLog, Long> {}
