package ee.tuleva.onboarding.oauth.server;

import static java.time.ZoneOffset.UTC;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

final class Timestamps {

  private Timestamps() {}

  static OffsetDateTime of(Instant instant) {
    return instant.atOffset(UTC);
  }

  static @Nullable OffsetDateTime ofNullable(@Nullable Instant instant) {
    return instant == null ? null : of(instant);
  }

  static @Nullable Instant read(ResultSet resultSet, String column) throws SQLException {
    var value = resultSet.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
