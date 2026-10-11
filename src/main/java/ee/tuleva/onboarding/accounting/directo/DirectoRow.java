package ee.tuleva.onboarding.accounting.directo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectoRow(
    @Nullable String account,
    @JsonProperty("debitamount") @Nullable BigDecimal debit,
    @JsonProperty("creditamount") @Nullable BigDecimal credit,
    @Nullable String object,
    @Nullable String project,
    @Nullable String supplier,
    @Nullable String customer,
    @Nullable String date) {}
