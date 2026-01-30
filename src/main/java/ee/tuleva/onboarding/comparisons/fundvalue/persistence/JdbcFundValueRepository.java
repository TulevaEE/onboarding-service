package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcFundValueRepository implements FundValueRepository, FundValueProvider {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private static final String FIND_LAST_VALUE_QUERY =
      """
      SELECT *
      FROM index_values
      WHERE key = :key
      ORDER BY date DESC NULLS LAST
      LIMIT 1
      """;

  private static final String FIND_LATEST_VALUE_QUERY =
      """
      SELECT *
      FROM index_values
      WHERE key = :key AND date <= :date
      ORDER BY date DESC
      LIMIT 1
      """;

  private static final String FIND_FUND_VALUE_QUERY =
      """
      SELECT *
      FROM index_values
      WHERE key = :key AND date = :date
      LIMIT 1
      """;

  private static final String INSERT_IF_NOT_EXISTS_QUERY =
      """
      INSERT INTO index_values (key, date, value, provider, updated_at)
      SELECT :key, :date, :value, :provider, :updatedAt
      WHERE NOT EXISTS (
          SELECT 1 FROM index_values WHERE key = :key AND date = :date
      )
      """;

  private static final String SELECT_GLOBAL_STOCK_VALUES_QUERY =
      """
      SELECT *
      FROM (
      (SELECT * FROM index_values WHERE key='MARKET' and date <= '2019-12-31' ORDER BY date DESC)
      UNION
      (SELECT * FROM index_values WHERE key='GLOBAL_STOCK_INDEX' and date >='2020-01-01')
      ) v ORDER BY v.date ASC
      """;

  private static final String FIND_EARLIEST_DATES_QUERY =
      """
      SELECT key, MIN(date) AS earliest_date
      FROM index_values
      GROUP BY key
      """;

  private static final String FIND_EARLIEST_DATE_FOR_KEY_QUERY =
      """
      SELECT key, MIN(date) AS earliest_date
      FROM index_values
      WHERE key = :key
      GROUP BY key
      """;

  @Override
  public Optional<LocalDate> findEarliestDateForKey(String key) {
    List<LocalDate> result =
        jdbcTemplate.query(
            FIND_EARLIEST_DATE_FOR_KEY_QUERY,
            Map.of("key", key),
            (rs, rowNum) -> rs.getDate("earliest_date").toLocalDate());
    return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
  }

  @Override
  public Map<String, LocalDate> findEarliestDates() {
    return jdbcTemplate.query(
        FIND_EARLIEST_DATES_QUERY,
        resultSet -> {
          Map<String, LocalDate> resultMap = new HashMap<>();
          while (resultSet.next()) {
            String key = resultSet.getString("key");
            LocalDate date = resultSet.getDate("earliest_date").toLocalDate();
            resultMap.put(key, date);
          }
          return resultMap;
        });
  }

  private static class FundValueRowMapper implements RowMapper<FundValue> {

    @Override
    public FundValue mapRow(ResultSet rs, int rowNum) throws SQLException {
      var timestamp = rs.getTimestamp("updated_at");
      return new FundValue(
          rs.getString("key"),
          rs.getDate("date").toLocalDate(),
          rs.getBigDecimal("value"),
          rs.getString("provider"),
          timestamp != null ? timestamp.toInstant() : null);
    }
  }

  @Override
  public Optional<FundValue> save(FundValue fundValue) {
    Map<String, Object> values = buildParamsMap(fundValue);
    try {
      int rowsAffected = jdbcTemplate.update(INSERT_IF_NOT_EXISTS_QUERY, values);
      return rowsAffected > 0 ? Optional.of(fundValue) : Optional.empty();
    } catch (DuplicateKeyException e) {
      return Optional.empty();
    }
  }

  private Map<String, Object> buildParamsMap(FundValue fundValue) {
    var map = new HashMap<String, Object>();
    map.put("key", fundValue.key());
    map.put("date", fundValue.date());
    map.put("value", fundValue.value());
    map.put("provider", fundValue.provider());
    map.put(
        "updatedAt",
        fundValue.updatedAt() != null ? java.sql.Timestamp.from(fundValue.updatedAt()) : null);
    return map;
  }

  @Override
  public List<FundValue> saveAll(List<FundValue> fundValues) {
    return fundValues.stream().map(this::save).flatMap(Optional::stream).toList();
  }

  @Override
  public Optional<FundValue> findLastValueForFund(String fund) {
    List<FundValue> result =
        jdbcTemplate.query(FIND_LAST_VALUE_QUERY, Map.of("key", fund), new FundValueRowMapper());
    return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
  }

  @Override
  public Optional<FundValue> getValueForDate(String key, LocalDate date) {
    List<FundValue> result =
        jdbcTemplate.query(
            FIND_FUND_VALUE_QUERY, Map.of("key", key, "date", date), new FundValueRowMapper());
    return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
  }

  @Override
  public Optional<FundValue> getLatestValue(String key, LocalDate date) {
    List<FundValue> result =
        jdbcTemplate.query(
            FIND_LATEST_VALUE_QUERY, Map.of("key", key, "date", date), new FundValueRowMapper());
    return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
  }

  @Override
  public List<FundValue> getGlobalStockValues() {
    return jdbcTemplate.query(SELECT_GLOBAL_STOCK_VALUES_QUERY, new FundValueRowMapper());
  }

  @Override
  public List<FundValue> findValuesBetweenDates(
      String fundKey, LocalDate startDate, LocalDate endDate) {
    String query =
        """
        SELECT *
        FROM index_values
        WHERE key = :key
        AND date BETWEEN :startDate AND :endDate
        ORDER BY date
        """;

    Map<String, Object> params =
        Map.of(
            "key", fundKey,
            "startDate", startDate,
            "endDate", endDate);

    return jdbcTemplate.query(query, params, new FundValueRowMapper());
  }
}
