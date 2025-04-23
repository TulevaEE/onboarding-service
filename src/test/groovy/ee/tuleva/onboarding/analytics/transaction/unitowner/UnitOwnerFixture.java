package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.epis.transaction.Pillar2DetailsDto;
import ee.tuleva.onboarding.epis.transaction.Pillar3DetailsDto;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerBalanceDto;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class UnitOwnerFixture {

  public static final String PERSON_ID_1 = "38001010001";
  public static final String PERSON_ID_2 = "49002020002";
  public static final String PERSON_ID_3 = "38503030003";
  public static final String PERSON_ID_4 = "49504040004";
  public static final String FUND_MANAGER = "Tuleva";
  public static final LocalDate SNAPSHOT_DATE_1 = LocalDate.of(2025, 4, 20);
  public static final LocalDate SNAPSHOT_DATE_2 = LocalDate.of(2025, 4, 21);

  public static UnitOwnerDto.UnitOwnerDtoBuilder dtoBuilder(String personId) {
    String firstName = "Mari";
    String lastName = "Maasikas";

    return UnitOwnerDto.builder()
        .personId(personId)
        .firstName(firstName)
        .name(lastName)
        .phone("555" + personId.substring(personId.length() - 5))
        .email(personId + "@test.com")
        .country("EE")
        .languagePreference("EST")
        .pensionAccount("EE123" + personId)
        .deathDate(null)
        .fundManager(FUND_MANAGER)
        .pillar2Details(pillar2DetailsDtoBuilder().build())
        .pillar3Details(pillar3DetailsDtoBuilder().build())
        .balances(
            List.of(
                unitOwnerBalanceDtoBuilder("TULEVA", "Tuleva II Pensionifond").build(),
                unitOwnerBalanceDtoBuilder("TULEVA3", "Tuleva III Pensionifond").build()));
  }

  public static Pillar2DetailsDto.Pillar2DetailsDtoBuilder pillar2DetailsDtoBuilder() {
    return Pillar2DetailsDto.builder()
        .choice("APPLICATION")
        .choiceMethod("WEB")
        .choiceDate(LocalDate.of(2018, 5, 10))
        .ravaDate(LocalDate.of(2018, 5, 11))
        .ravaStatus("ACTIVE")
        .rate(2)
        .nextRate(2)
        .nextRateDate(LocalDate.of(2026, 1, 1));
  }

  public static Pillar3DetailsDto.Pillar3DetailsDtoBuilder pillar3DetailsDtoBuilder() {
    return Pillar3DetailsDto.builder()
        .identificationDate(LocalDate.of(2019, 1, 15))
        .identifier("ID_CARD")
        .blockFlag("N")
        .blocker(null);
  }

  public static UnitOwnerBalanceDto.UnitOwnerBalanceDtoBuilder unitOwnerBalanceDtoBuilder(
      String shortName, String name) {
    return UnitOwnerBalanceDto.builder()
        .securityShortName(shortName)
        .securityName(name)
        .type("FUND_UNIT")
        .amount(BigDecimal.valueOf(1234.5678))
        .startDate(LocalDate.of(2018, 5, 11))
        .lastUpdated(LocalDate.of(2025, 4, 17));
  }

  public static UnitOwner.UnitOwnerBuilder entityBuilder(
      String personId, LocalDate snapshotDate, LocalDateTime created) {

    String firstName = "Mari";
    String lastName = "Maasikas";

    return UnitOwner.builder()
        .personalId(personId)
        .snapshotDate(snapshotDate)
        .firstName(firstName)
        .lastName(lastName)
        .phone("555" + personId.substring(personId.length() - 5))
        .email(personId + "@test.com")
        .country("EE")
        .languagePreference("EST")
        .pensionAccount("EE123" + personId)
        .deathDate(null)
        .fundManager(FUND_MANAGER)
        .p2choice("APPLICATION")
        .p2choiceMethod("WEB")
        .p2choiceDate(LocalDate.of(2018, 5, 10))
        .p2ravaDate(LocalDate.of(2018, 5, 11))
        .p2ravaStatus("ACTIVE")
        .p2rate(2)
        .p2nextRate(2)
        .p2nextRateDate(LocalDate.of(2026, 1, 1))
        .p3identificationDate(LocalDate.of(2019, 1, 15))
        .p3identifier("ID_CARD")
        .p3blockFlag("N")
        .balances(
            List.of(
                unitOwnerBalanceEmbeddableBuilder("TULEVA", "Tuleva II Pensionifond").build(),
                unitOwnerBalanceEmbeddableBuilder("TULEVA3", "Tuleva III Pensionifond").build()))
        .dateCreated(created);
  }

  public static UnitOwnerBalanceEmbeddable.UnitOwnerBalanceEmbeddableBuilder
      unitOwnerBalanceEmbeddableBuilder(String shortName, String name) {
    return UnitOwnerBalanceEmbeddable.builder()
        .securityShortName(shortName)
        .securityName(name)
        .type("FUND_UNIT")
        .amount(BigDecimal.valueOf(1234.5678))
        .startDate(LocalDate.of(2018, 5, 11))
        .lastUpdated(LocalDate.of(2025, 4, 17));
  }
}
