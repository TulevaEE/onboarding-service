package ee.tuleva.onboarding.accounting.directo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectoAccount(
    @Nullable String code,
    @Nullable String name,
    @JsonProperty("class") @Nullable String accountClass,
    @JsonProperty("correspondancecode") @Nullable String correspondenceCode,
    @Nullable List<DirectoDatafield> datafields) {}
