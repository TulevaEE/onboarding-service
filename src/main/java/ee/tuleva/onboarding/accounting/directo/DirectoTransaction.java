package ee.tuleva.onboarding.accounting.directo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectoTransaction(
    @Nullable String type,
    @Nullable String number,
    @Nullable String date,
    @Nullable List<DirectoRow> rows) {}
