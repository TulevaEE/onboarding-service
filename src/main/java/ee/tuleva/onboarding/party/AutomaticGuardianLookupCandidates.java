package ee.tuleva.onboarding.party;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AutomaticGuardianLookupCandidates {

  static final int RECENT_PAYOUT_DAYS = 7;

  static final List<String> THIRD_PILLAR_PAYOUT_TYPES =
      List.of("Lunastamine", "Osakute ülekanne", "Broneeritud osakute kustutamine");

  private final JdbcClient database;
  private final Clock clock;

  List<String> firstSeenHoldersAndRecentThirdPillarPayouts() {
    return database
        .sql(
            """
            SELECT DISTINCT TRIM(latest.personal_id) AS code
            FROM unit_owner latest
            WHERE latest.snapshot_date = (SELECT MAX(snapshot_date) FROM unit_owner)
              AND NOT EXISTS (SELECT 1 FROM unit_owner earlier
                              WHERE earlier.personal_id = latest.personal_id
                                AND earlier.snapshot_date < latest.snapshot_date)
            UNION
            SELECT DISTINCT TRIM(payout.personal_id) AS code
            FROM analytics.third_pillar_transactions payout
            WHERE payout.transaction_type IN (:payoutTypes)
              AND payout.reporting_date >= :since
            """)
        .param("payoutTypes", THIRD_PILLAR_PAYOUT_TYPES)
        .param("since", LocalDate.now(clock).minusDays(RECENT_PAYOUT_DAYS))
        .query((row, number) -> row.getString("code"))
        .list()
        .stream()
        .filter(Objects::nonNull)
        .toList();
  }
}
