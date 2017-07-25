package ee.tuleva.onboarding.audit;

import org.springframework.data.repository.CrudRepository;

public interface AuditLogRepository extends CrudRepository<AuditLog, Long> {

//    @Query("select ae from AuditEvent ae " +
//            "where ae.timestamp > :after")
//    List<AuditLog> findByTimestampAfter(Instant after);

//    @Query("select ae from AuditEvent ae " +
//            "where ae.principal = :principal and ae.timestamp > :after")
//    List<AuditLog> findByPrincipalAndTimestampAfter(String principal, Instant after);

//    @Query("select ae from AuditEvent ae " +
//            "where ae.type = :type and ae.principal = :principal and ae.timestamp > :after")
//    List<AuditLog> findByTypeAndPrincipalAndTimestampAfter(String type, String principal, Instant after);

}
