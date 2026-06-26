package ee.tuleva.onboarding.populationregister;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
record PersonResponse(
    @JsonProperty("isikukood") @Nullable String personalCode,
    @JsonProperty("eesnimi") @Nullable String firstName,
    @JsonProperty("perekonnanimi") @Nullable String lastName,
    @JsonProperty("synniKuupaev") @Nullable String dateOfBirth,
    @JsonProperty("isikuStaatus") @Nullable Code status,
    @JsonProperty("pohiKodakondsus") @Nullable Citizenship citizenship,
    @JsonProperty("hooldusoigused") @Nullable List<Custody> custodyRights) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Code(
      @JsonProperty("elemendiKood") @Nullable String code,
      @JsonProperty("nimetus") @Nullable String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Citizenship(@JsonProperty("riik") @Nullable Code country) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Custody(
      @JsonProperty("liik") @Nullable Code type,
      @JsonProperty("staatus") @Nullable Code status,
      @JsonProperty("teineIsikIsikukood") @Nullable String otherPersonCode,
      @JsonProperty("teineIsikOlek") @Nullable Code otherPersonStatus) {}
}
