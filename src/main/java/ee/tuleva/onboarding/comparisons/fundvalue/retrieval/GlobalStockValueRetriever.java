package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalStockValueRetriever implements ComparisonIndexRetriever {
    public static final String KEY = "NEW_GLOBAL_STOCK_INDEX";

    private final FundValueRepository fundValueRepository;

    public static final BigDecimal MULTIPLIER = new BigDecimal("13.3883094");

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
        return createStocks();
    }

    private List<FundValue> createStocks() {
        List<FundValue> stockValues = new ArrayList<>();
        List<FundValue> jdbcValues = fundValueRepository.getGlobalStockValues();

        for (FundValue fundvalue : jdbcValues) {
            LocalDate date = fundvalue.getDate();
            LocalDate changeDate = LocalDate.of(2019, 12, 31);
            BigDecimal value = fundvalue.getValue();

            if (date.isAfter(changeDate)) {
                BigDecimal stockValue = value.divide(MULTIPLIER, RoundingMode.HALF_UP);
                stockValues.add(new FundValue(KEY, date, stockValue));
            } else {
                stockValues.add(new FundValue(KEY, date, value));
            }
        }

        return stockValues;
    }
}
