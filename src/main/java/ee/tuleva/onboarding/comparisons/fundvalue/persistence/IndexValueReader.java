package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import static ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository.FIND_LATEST_DATE_BY_KEYS_QUERY;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository.FundValueRowMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IndexValueReader {

  private static final String FIND_LATEST_VALUES_BY_KEYS_QUERY =
      """
      SELECT iv.*
      FROM index_values iv
      INNER JOIN (%s) latest ON iv.key = latest.key AND iv.date = latest.latest_date
      ORDER BY iv.key
      """
          .formatted(FIND_LATEST_DATE_BY_KEYS_QUERY);

  private static final String VALUES_BETWEEN_DATES_FOR_KEYS =
      """
      FROM index_values
      WHERE key IN (:keys)
      AND date BETWEEN :startDate AND :endDate
      """;

  private final JdbcClient jdbcClient;

  public List<FundValue> findLatestValuesByKeys(List<String> keys) {
    if (keys.isEmpty()) {
      return List.of();
    }
    return jdbcClient
        .sql(FIND_LATEST_VALUES_BY_KEYS_QUERY)
        .param("keys", keys)
        .query(new FundValueRowMapper())
        .list();
  }

  public long countValuesBetweenDatesForKeys(
      List<String> keys, LocalDate startDate, LocalDate endDate) {
    if (keys.isEmpty()) {
      return 0;
    }
    return jdbcClient
        .sql("SELECT COUNT(*) " + VALUES_BETWEEN_DATES_FOR_KEYS)
        .params(betweenDatesForKeys(keys, startDate, endDate))
        .query(Long.class)
        .single();
  }

  public List<FundValue> findValuesBetweenDatesForKeys(
      List<String> keys, LocalDate startDate, LocalDate endDate, int maxRows) {
    if (keys.isEmpty()) {
      return List.of();
    }
    return jdbcClient
        .sql("SELECT * " + VALUES_BETWEEN_DATES_FOR_KEYS + "ORDER BY key, date LIMIT :maxRows")
        .params(betweenDatesForKeys(keys, startDate, endDate))
        .param("maxRows", maxRows)
        .query(new FundValueRowMapper())
        .list();
  }

  private static Map<String, Object> betweenDatesForKeys(
      List<String> keys, LocalDate startDate, LocalDate endDate) {
    return Map.of("keys", keys, "startDate", startDate, "endDate", endDate);
  }
}
