package ee.tuleva.onboarding.investment.event.trigger;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface JobTriggerRepository extends JpaRepository<JobTrigger, Long> {

  List<JobTrigger> findByStatusOrderByCreatedAtAsc(String status);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update JobTrigger t set t.status = 'PROCESSING', t.startedAt = coalesce(t.startedAt, :now)"
          + " where t.id = :id and t.status = 'PENDING'")
  int claim(@Param("id") Long id, @Param("now") Instant now);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update JobTrigger t set t.status = 'COMPLETED', t.completedAt = :now"
          + " where t.id = :id and t.status = 'PROCESSING'")
  int complete(@Param("id") Long id, @Param("now") Instant now);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update JobTrigger t set t.status = 'FAILED', t.completedAt = :now,"
          + " t.errorMessage = :errorMessage where t.id = :id and t.status = :expectedStatus")
  int fail(
      @Param("id") Long id,
      @Param("expectedStatus") String expectedStatus,
      @Param("now") Instant now,
      @Param("errorMessage") String errorMessage);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update JobTrigger t set t.status = 'PENDING' where t.id = :id and t.status = 'PROCESSING'")
  int requeue(@Param("id") Long id);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update JobTrigger t set t.createdAt = :now"
          + " where t.jobName = :jobName and t.status = 'PENDING'")
  int restartPendingBudget(@Param("jobName") String jobName, @Param("now") Instant now);
}
