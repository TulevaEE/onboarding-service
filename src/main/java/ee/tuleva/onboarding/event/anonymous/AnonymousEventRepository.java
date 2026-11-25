package ee.tuleva.onboarding.event.anonymous;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
class AnonymousEventRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  void save(AnonymousEventCommand command, Instant timestamp) {
    jdbcClient
        .sql(
            """
            INSERT INTO anonymous_event (timestamp, type, visit_id, device, data)
            VALUES (:timestamp, :type, :visitId, :device, CAST(:data AS JSONB))
            """)
        .param("timestamp", Timestamp.from(timestamp))
        .param("type", String.valueOf(command.getType()))
        .param("visitId", command.getVisitId())
        .param("device", command.getDevice())
        .param("data", objectMapper.writeValueAsString(command.getData()))
        .update();
  }

  int deleteOlderThan(Instant cutoff) {
    return jdbcClient
        .sql("DELETE FROM anonymous_event WHERE timestamp < :cutoff")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
  }
}
