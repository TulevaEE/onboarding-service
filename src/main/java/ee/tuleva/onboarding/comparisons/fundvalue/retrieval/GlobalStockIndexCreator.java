package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Slf4j
@Service
public class GlobalStockIndexCreator implements ComparisonIndexRetriever  {
    public static final String KEY = "NEW_GLOBAL_STOCK_INDEX";

    //private NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL = "" +
        "SELECT * " +
        "FROM ( " +
        "(SELECT * FROM index_values WHERE key='MARKET' and date <= '2019-12-31' ORDER BY date DESC LIMIT 4) " +
        "UNION " +
        "(SELECT * FROM index_values WHERE key='GLOBAL_STOCK_INDEX' and date >='2020-01-01' LIMIT 4) " +
        ") values ORDER BY values.date ASC";

    //added own postgres connection
    private final String url = "jdbc:postgresql://localhost/tuleva"; //create own local database
    private final String user = ""; //add own username
    private final String password = ""; //add own password

    @Override
    public String getKey() {
        return KEY;
    }

    public Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to the PostgreSQL server successfully.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return conn;
    }

    public List<FundValue> getStockInfo() throws IOException {
        List<FundValue> stockValues = new ArrayList<>();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL)) {
            // display query information
            while (rs.next()) {
                String queryDate = rs.getString("date");
                String queryValue = rs.getString("value");

                LocalDate date = LocalDate.parse(queryDate);
                LocalDate changeDate = LocalDate.of(2019, 12, 31);

                if (date.isAfter(changeDate)) {
                    Double convertedNumber = Double.parseDouble(queryValue);
                    convertedNumber = convertedNumber / 13.3883094;
                    BigDecimal stockValue = new BigDecimal(convertedNumber);
                    stockValues.add(new FundValue(KEY, date, stockValue));
                }
                else {
                    BigDecimal stockValue = new BigDecimal(queryValue);
                    stockValues.add(new FundValue(KEY, date, stockValue));
                }

            }

        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }

        return stockValues;
    }

    private List<FundValue> getStockValues() {
        try {
            return getStockInfo();
        } catch (IOException e) {
            throw new IllegalStateException("Could not get Global Stock Index values", e);
        }
    }

    @Override
    public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
        List<FundValue> stockValues = getStockValues();
        return stockValues.stream().filter(fundValue -> {
            LocalDate date = fundValue.getDate();
            return (startDate.isBefore(date) || startDate.equals(date)) && (endDate.isAfter(date) || endDate.equals(date));
        }).collect(toList());
    }

    public static void main(String[] args) {
        GlobalStockIndexCreator app = new GlobalStockIndexCreator();

        //create own values for input
        LocalDate begin = LocalDate.parse("2019-12-26");
        LocalDate end = LocalDate.parse("2020-01-04");

        System.out.println(app.retrieveValuesForRange(begin, end));
    }
}
