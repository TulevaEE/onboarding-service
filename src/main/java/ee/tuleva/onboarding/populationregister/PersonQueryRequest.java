package ee.tuleva.onboarding.populationregister;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record PersonQueryRequest(
    @JsonProperty("isikukoodid") List<String> personalCodes,
    @JsonProperty("andmevaljad") DataFields dataFields) {

  static PersonQueryRequest forIdentity(String personalCode) {
    return new PersonQueryRequest(List.of(personalCode), DataFields.identity());
  }

  static PersonQueryRequest forCustody(String personalCode) {
    return new PersonQueryRequest(List.of(personalCode), DataFields.custody());
  }

  record DataFields(
      @JsonProperty("isikuandmed") List<String> personData,
      @JsonProperty("muudPerenimedDetail") List<String> previousSurnames,
      @JsonProperty("kodakondsused") List<String> citizenships,
      @JsonProperty("isikuAadressid") List<String> addresses,
      @JsonProperty("isikuKontaktid") List<String> contacts,
      @JsonProperty("dokumendid") List<String> documents,
      @JsonProperty("suhted") List<String> relationships,
      @JsonProperty("hooldusoigused") List<String> custodyRights) {

    private static final List<String> EXCLUDED = List.of();

    static DataFields identity() {
      return new DataFields(
          List.of(
              "Isikukood",
              "Eesnimi",
              "Perekonnanimi",
              "SynniKuupaev",
              "IsikuStaatus",
              "PohiKodakondsus"),
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED);
    }

    static DataFields custody() {
      return new DataFields(
          List.of("Isikukood", "Eesnimi", "Perekonnanimi", "IsikuStaatus"),
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          EXCLUDED,
          List.of(
              "Liik",
              "Staatus",
              "HooldusoigusAlgus",
              "TeineIsikIsikukood",
              "TeineIsikOlek",
              "TeineIsikEesnimi",
              "TeineIsikPerenimi"));
    }
  }
}
