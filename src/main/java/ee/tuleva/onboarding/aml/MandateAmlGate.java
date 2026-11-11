package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.DOCUMENT;
import static ee.tuleva.onboarding.aml.AmlCheckType.OCCUPATION;
import static ee.tuleva.onboarding.aml.AmlCheckType.PENSION_REGISTRY_NAME;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_OVERRIDE;
import static ee.tuleva.onboarding.aml.AmlCheckType.RESIDENCY_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.RESIDENCY_MANUAL;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION_OVERRIDE;
import static ee.tuleva.onboarding.aml.AmlCheckType.SK_NAME;
import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.rulebook.Rule;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class MandateAmlGate {

  record RecentChecks(Set<AmlCheckType> recorded, Set<AmlCheckType> passed) {

    RecentChecks {
      recorded = Set.copyOf(recorded);
      passed = Set.copyOf(passed);
    }

    static RecentChecks of(List<AmlCheck> checks) {
      return new RecentChecks(
          checks.stream().map(AmlCheck::getType).collect(toSet()),
          checks.stream().filter(AmlCheck::isSuccess).map(AmlCheck::getType).collect(toSet()));
    }

    static RecentChecks allPassed(AmlCheckType... types) {
      return new RecentChecks(Set.of(types), Set.of(types));
    }

    RecentChecks withFailed(AmlCheckType type) {
      return new RecentChecks(
          Stream.concat(recorded.stream(), Stream.of(type)).collect(toSet()), passed);
    }
  }

  private static final Fact<RecentChecks> PEP_DECLARED =
      new Fact<>(
          new Phrase(
              "isik on andnud PEP-deklaratsiooni",
              "isik ei ole andnud PEP-deklaratsiooni",
              "arvesse lähevad viimase 365 päeva kontrollid",
              "kontroll on läbitud, kui sellel perioodil on vähemalt üks edukas tulemus",
              "PEP-deklaratsioon loetakse antuks olenemata sellest, mida isik deklareeris"),
          checks -> checks.recorded().contains(POLITICALLY_EXPOSED_PERSON));

  private static final Fact<RecentChecks> PEP_SCREENED =
      passed(
          POLITICALLY_EXPOSED_PERSON_AUTO,
          "automaatne PEP-kontroll on läbitud",
          "automaatne PEP-kontroll ei ole läbitud");

  private static final Fact<RecentChecks> PEP_OVERRIDDEN =
      passed(
          POLITICALLY_EXPOSED_PERSON_OVERRIDE,
          "PEP-kontrolli tulemus on käsitsi üle kinnitatud",
          "PEP-kontrolli tulemust ei ole käsitsi üle kinnitatud");

  private static final Fact<RecentChecks> SANCTIONS_SCREENED =
      passed(SANCTION, "sanktsioonikontroll on läbitud", "sanktsioonikontroll ei ole läbitud");

  private static final Fact<RecentChecks> SANCTIONS_OVERRIDDEN =
      passed(
          SANCTION_OVERRIDE,
          "sanktsioonikontrolli tulemus on käsitsi üle kinnitatud",
          "sanktsioonikontrolli tulemust ei ole käsitsi üle kinnitatud");

  private static final Fact<RecentChecks> NAME_MATCHES_SK =
      passed(SK_NAME, "nimi vastab SK andmetele", "nime vastavust SK andmetele ei ole kinnitatud");

  private static final Fact<RecentChecks> NAME_MATCHES_PENSION_REGISTRY =
      passed(
          PENSION_REGISTRY_NAME,
          "nimi vastab Pensionikeskuse andmetele",
          "nime vastavust Pensionikeskuse andmetele ei ole kinnitatud");

  private static final Fact<RecentChecks> DOCUMENT_CHECKED =
      passed(
          DOCUMENT,
          "isikut tõendav dokument on kontrollitud",
          "isikut tõendavat dokumenti ei ole kontrollitud");

  private static final Fact<RecentChecks> OCCUPATION_CHECKED =
      passed(OCCUPATION, "tegevusala kontroll on läbitud", "tegevusala kontroll ei ole läbitud");

  private static final Fact<RecentChecks> RESIDENCY_SCREENED =
      passed(
          RESIDENCY_AUTO,
          "elukoha automaatne kontroll on läbitud",
          "elukoha automaatne kontroll ei ole läbitud");

  private static final Fact<RecentChecks> RESIDENCY_CHECKED_BY_HAND =
      passed(
          RESIDENCY_MANUAL,
          "elukoht on käsitsi kontrollitud",
          "elukohta ei ole käsitsi kontrollitud");

  static final Rule<RecentChecks> SISEKORD_13_P_4_1_7 =
      new Rule<>(
          "4.1.7",
          "Vabatahtliku pensioni avaldus võetakse vastu pärast isiku tuvastamist",
          and(
              or(PEP_DECLARED, PEP_SCREENED, PEP_OVERRIDDEN),
              or(SANCTIONS_SCREENED, SANCTIONS_OVERRIDDEN),
              or(NAME_MATCHES_SK, NAME_MATCHES_PENSION_REGISTRY),
              DOCUMENT_CHECKED,
              OCCUPATION_CHECKED,
              or(RESIDENCY_SCREENED, RESIDENCY_CHECKED_BY_HAND)),
          List.of(
              applies(
                  "kõik automaatsed kontrollid on läbitud",
                  RecentChecks.allPassed(
                      POLITICALLY_EXPOSED_PERSON_AUTO,
                      SANCTION,
                      SK_NAME,
                      DOCUMENT,
                      OCCUPATION,
                      RESIDENCY_AUTO)),
              applies(
                  "nimi vastab ainult Pensionikeskuse andmetele ja tulemused on käsitsi üle"
                      + " kinnitatud",
                  RecentChecks.allPassed(
                      POLITICALLY_EXPOSED_PERSON_OVERRIDE,
                      SANCTION_OVERRIDE,
                      PENSION_REGISTRY_NAME,
                      DOCUMENT,
                      OCCUPATION,
                      RESIDENCY_MANUAL)),
              applies(
                  "PEP-deklaratsiooni kontroll ei õnnestunud",
                  RecentChecks.allPassed(SANCTION, SK_NAME, DOCUMENT, OCCUPATION, RESIDENCY_AUTO)
                      .withFailed(POLITICALLY_EXPOSED_PERSON)),
              doesNotApply(
                  "sanktsioonikontroll puudub",
                  RecentChecks.allPassed(
                      POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, OCCUPATION, RESIDENCY_AUTO)),
              doesNotApply(
                  "nime ei ole kummagi registriga võrreldud",
                  RecentChecks.allPassed(
                      POLITICALLY_EXPOSED_PERSON,
                      SANCTION,
                      DOCUMENT,
                      OCCUPATION,
                      RESIDENCY_MANUAL)),
              doesNotApply(
                  "tegevusala kontroll puudub",
                  RecentChecks.allPassed(
                      POLITICALLY_EXPOSED_PERSON_AUTO,
                      SANCTION,
                      SK_NAME,
                      DOCUMENT,
                      RESIDENCY_AUTO)),
              doesNotApply(
                  "isikut tõendavat dokumenti ei ole kontrollitud",
                  RecentChecks.allPassed(
                      POLITICALLY_EXPOSED_PERSON_AUTO,
                      SANCTION,
                      SK_NAME,
                      OCCUPATION,
                      RESIDENCY_AUTO)),
              doesNotApply("kontrolle ei ole", RecentChecks.allPassed())));

  private MandateAmlGate() {}

  private static Fact<RecentChecks> passed(AmlCheckType type, String holds, String fails) {
    return new Fact<>(new Phrase(holds, fails), checks -> checks.passed().contains(type));
  }
}
