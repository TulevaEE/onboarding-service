package ee.tuleva.onboarding.comparisons.fundvalue.persistence;

import com.google.common.collect.ImmutableMap;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;


@Repository
@RequiredArgsConstructor
public class JdbcFundValueRepository implements FundValueRepository, FundValueProvider {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String FIND_LAST_VALUE_QUERY = "" +
        "SELECT * " +
        "FROM index_values " +
        "WHERE key = :key " +
        "ORDER BY date DESC NULLS LAST " +
        "LIMIT 1";

    private static final String FIND_LATEST_VALUE_QUERY = "" +
        "SELECT * " +
        "FROM index_values " +
        "WHERE key = :key AND date <= :date " +
        "ORDER BY date DESC " +
        "LIMIT 1";

    private static final String FIND_FUND_VALUE_QUERY = "" +
        "SELECT * " +
        "FROM index_values " +
        "WHERE key = :key AND date = :date " +
        "LIMIT 1";

    private static final String ALL_KEYS_QUERY =
        "SELECT DISTINCT key FROM index_values ORDER BY key";

    private static final String INSERT_VALUES_QUERY = "" +
        "INSERT INTO index_values (key, date, value) " +
        "VALUES (:key, :date, :value)";

    private static final String UPDATE_VALUES_QUERY = "" +
        "UPDATE index_values SET value = :value " +
        "WHERE key = :key AND date = :date";

    private static final String SELECT_GLOBAL_STOCK_VALUES_QUERY = "" +
        "SELECT * " +
        "FROM ( " +
        "(SELECT * FROM index_values WHERE key='MARKET' and date <= '2019-12-31' ORDER BY date DESC) " +
        "UNION " +
        "(SELECT * FROM index_values WHERE key='GLOBAL_STOCK_INDEX' and date >='2020-01-01') " +
        ") values ORDER BY values.date ASC";

    private static class FundValueRowMapper implements RowMapper<FundValue> {
        @Override
        public FundValue mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FundValue(rs.getString("key"), rs.getDate("date").toLocalDate(), rs.getBigDecimal("value"));
        }
    }

    @Override
    public void save(FundValue fundValue) {
        Map<String, Object> values = ImmutableMap.of(
            "key", fundValue.getComparisonFund(),
            "date", fundValue.getDate(),
            "value", fundValue.getValue()
        );

        jdbcTemplate.update(INSERT_VALUES_QUERY, values);
    }

    @Override
    public void update(FundValue fundValue) {
        Map<String, Object> values = ImmutableMap.of(
            "key", fundValue.getComparisonFund(),
            "date", fundValue.getDate(),
            "value", fundValue.getValue()
        );

        jdbcTemplate.update(UPDATE_VALUES_QUERY, values);
    }

    @Override
    public void saveAll(List<FundValue> fundValues) {
        List<Map<String, Object>> batchValues = fundValues.stream()
            .map(fundValue -> ImmutableMap.of(
                "key", (Object) fundValue.getComparisonFund(),
                "date", fundValue.getDate(),
                "value", fundValue.getValue()))
            .collect(toList());
        jdbcTemplate.batchUpdate(INSERT_VALUES_QUERY, batchValues.toArray(new Map[fundValues.size()]));
    }

    @Override
    public Optional<FundValue> findLastValueForFund(String fund) {
        List<FundValue> result = jdbcTemplate.query(
            FIND_LAST_VALUE_QUERY,
            ImmutableMap.of("key", fund),
            new FundValueRowMapper()
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Optional<FundValue> findExistingValueForFund(FundValue fundValue) {
        List<FundValue> result = jdbcTemplate.query(
            FIND_FUND_VALUE_QUERY,
            ImmutableMap.of(
                "key", fundValue.getComparisonFund(),
                "date", fundValue.getDate(),
                "value", fundValue.getValue()
            ),
            new FundValueRowMapper()
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<String> findAllKeys() {
        return jdbcTemplate.queryForList(ALL_KEYS_QUERY, ImmutableMap.of(), String.class);
    }

    @Override
    public Optional<FundValue> getLatestValue(String key, LocalDate date) {
        List<FundValue> result = jdbcTemplate.query(
            FIND_LATEST_VALUE_QUERY,
            ImmutableMap.of("key", key, "date", date),
            new FundValueRowMapper()
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<FundValue> getGlobalStockValues() {
        return jdbcTemplate.query(SELECT_GLOBAL_STOCK_VALUES_QUERY, new FundValueRowMapper());
    }
}
