package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Slf4j
@Service
public class GlobalStockIndexCreator implements ComparisonIndexRetriever {
    public static final String KEY = "NEW_GLOBAL_STOCK_INDEX";

    NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL = "" +
        "SELECT * " +
        "FROM ( " +
        "(SELECT * FROM index_values WHERE key='MARKET' and date <= '2019-12-31' ORDER BY date DESC LIMIT 4) " +
        "UNION " +
        "(SELECT * FROM index_values WHERE key='GLOBAL_STOCK_INDEX' and date >='2020-01-01' LIMIT 4) " +
        ") values ORDER BY values.date ASC";

    private static final class FundValueRowMapper implements RowMapper<FundValue> {
        @Override
        public FundValue mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FundValue(rs.getString("key"), rs.getDate("date").toLocalDate(), rs.getBigDecimal("value"));
        }
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        List<FundValue> stockValues = getStockValues();
        return stockValues.stream().filter(fundValue -> {
            LocalDate date = fundValue.getDate();
            return (startDate.isBefore(date) || startDate.equals(date)) && (endDate.isAfter(date) || endDate.equals(date));
        }).collect(toList());
    }

    private List<FundValue> getStockValues() {
        try {
            return createStockList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not get Global Stock Index values", e);
        }
    }


    private List<FundValue> createStockList() throws IOException {
        List<FundValue> stockValues = new ArrayList<>();
        List<FundValue> jdbcValues = getValueFromRepository();

        for (int i = 0; i < jdbcValues.size(); i++) {
            FundValue fundvalue = jdbcValues.get(i);

            LocalDate date = fundvalue.getDate();
            LocalDate changeDate = LocalDate.of(2019, 12, 31);
            BigDecimal value = fundvalue.getValue();

            if (date.isAfter(changeDate)) {
                Double convertedNumber = value.doubleValue();
                convertedNumber = convertedNumber / 13.3883094;
                BigDecimal stockValue = new BigDecimal(convertedNumber);
                stockValues.add(new FundValue(KEY, date, stockValue));
            } else {
                BigDecimal stockValue = value;
                stockValues.add(new FundValue(KEY, date, stockValue));
            }
        }

        return stockValues;
    }


    private List<FundValue> getValueFromRepository() {
        List<FundValue> stockValues = jdbcTemplate.query(SQL, new FundValueRowMapper());
        return stockValues;
    }
}
