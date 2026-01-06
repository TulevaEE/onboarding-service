package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class UnionStockIndexRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "UNION_STOCK_INDEX";
  public static final String PROVIDER = "CALCULATED";

  private final FundValueRepository fundValueRepository;

  public static final BigDecimal MULTIPLIER = new BigDecimal("13.3883094");

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    List<FundValue> stockValues = getStockValues();
    return stockValues.stream()
        .filter(
            fundValue -> {
              LocalDate date = fundValue.date();
              return (startDate.isBefore(date) || startDate.equals(date))
                  && (endDate.isAfter(date) || endDate.equals(date));
            })
        .collect(toList());
  }

  private List<FundValue> getStockValues() {
    return createStocks();
  }

  private List<FundValue> createStocks() {
    List<FundValue> stockValues = new ArrayList<>();
    List<FundValue> jdbcValues = fundValueRepository.getGlobalStockValues();
    var now = Instant.now();

    for (FundValue fundvalue : jdbcValues) {
      LocalDate date = fundvalue.date();
      LocalDate changeDate = LocalDate.of(2019, 12, 31);
      BigDecimal value = fundvalue.value();

      if (date.isAfter(changeDate)) {
        BigDecimal stockValue = value.divide(MULTIPLIER, RoundingMode.HALF_UP);
        stockValues.add(new FundValue(KEY, date, stockValue, PROVIDER, now));
      } else {
        stockValues.add(new FundValue(KEY, date, value, PROVIDER, now));
      }
    }

    return stockValues;
  }
}
