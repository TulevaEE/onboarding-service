package ee.tuleva.onboarding.aml.health;

import ee.tuleva.onboarding.aml.AmlCheck;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AmlCheckHealthRepository extends JpaRepository<AmlCheck, Long> {

  @Query(
      value =
          """
        WITH filtered_checks AS (
            SELECT
                type,
                created_time
            FROM
                public.aml_check
            WHERE
                created_time >= :sinceTime
        ),
        ordered_checks_by_type AS (
            SELECT
                type,
                created_time,
                LAG(created_time, 1) OVER (PARTITION BY type ORDER BY created_time) AS prev_created_time_for_type
            FROM
                filtered_checks
        ),
        time_diffs_by_type AS (
            SELECT
                type,
                (created_time - prev_created_time_for_type) AS interval_duration
            FROM
                ordered_checks_by_type
            WHERE
                prev_created_time_for_type IS NOT NULL
        )
        SELECT
            type,
            EXTRACT(EPOCH FROM MAX(interval_duration)) AS max_interval_seconds
        FROM
            time_diffs_by_type
        WHERE
            interval_duration IS NOT NULL
            AND interval_duration > INTERVAL '0' SECOND
        GROUP BY
            type
        ORDER BY
            type
    """,
      nativeQuery = true)
  List<AmlCheckTypeHealthThreshold> findMaxIntervalSecondsPerTypeSince(
      @Param("sinceTime") Instant sinceTime);

  @Query(
      value =
          """
        SELECT
            MAX(ac.created_time)
        FROM
            public.aml_check ac
        WHERE
            ac.type = :checkType
    """,
      nativeQuery = true)
  Optional<LocalDateTime> findLastCheckTimeByType(@Param("checkType") String checkType);
}
