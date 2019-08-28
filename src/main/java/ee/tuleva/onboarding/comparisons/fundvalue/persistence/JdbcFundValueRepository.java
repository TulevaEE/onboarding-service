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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Repository
@RequiredArgsConstructor
public class JdbcFundValueRepository implements FundValueRepository, FundValueProvider {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String FIND_LAST_VALUE_QUERY = "" +
        "SELECT * " +
        "FROM index_values " +
        "WHERE key=:key " +
        "ORDER BY date DESC NULLS LAST " +
        "LIMIT 1";

    private static final String FIND_LATEST_VALUE_QUERY = "" +
        "SELECT * " +
        "FROM index_values " +
        "WHERE key=:key AND date <= :date " +
        "ORDER BY date DESC " +
        "LIMIT 1";

    private static final String INSERT_VALUES_QUERY = "" +
        "INSERT INTO index_values (key, date, value) " +
        "VALUES (:key, :date, :value)";

    private class FundValueRowMapper implements RowMapper<FundValue> {
        @Override
        public FundValue mapRow(ResultSet rs, int rowNum) throws SQLException {
            return FundValue.builder()
                .comparisonFund(rs.getString("key"))
                .time(rs.getTimestamp("date").toInstant())
                .value(rs.getBigDecimal("value"))
                .build();
        }
    }

    @Override
    public void saveAll(List<FundValue> fundValues) {
        List<Map<String, Object>> batchValues = fundValues.stream()
            .map(fundValue -> ImmutableMap.of(
                "key", (Object)fundValue.getComparisonFund(),
                "date", Timestamp.from(fundValue.getTime()),
                "value", fundValue.getValue()))
            .collect(Collectors.toList());
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
    public Optional<FundValue> getLatestValue(String key, LocalDate date) {
        List<FundValue> result = jdbcTemplate.query(
            FIND_LATEST_VALUE_QUERY,
            ImmutableMap.of("key", key, "date", date),
            new FundValueRowMapper()
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
