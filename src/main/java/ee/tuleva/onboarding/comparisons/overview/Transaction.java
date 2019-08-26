package ee.tuleva.onboarding.comparisons.overview;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Value
@RequiredArgsConstructor
public class Transaction {
    BigDecimal amount;
    LocalDate date;

    public Transaction(BigDecimal amount, Instant date) {
        this.amount = amount;
        this.date = date.atZone(ZoneId.of("Europe/Tallinn")).toLocalDate();
    }
}
