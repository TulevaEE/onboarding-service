package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Residual.AT_THRESHOLD;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Residual.BELOW_THRESHOLD;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.COMPLETED;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.FAILED;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.INCOMPLETE_PRICES;
import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;

import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.rulebook.Rule;
import java.util.List;

public final class NavReportHold {

  public enum Run {
    COMPLETED,
    INCOMPLETE_PRICES,
    FAILED
  }

  public enum Residual {
    AT_THRESHOLD,
    BELOW_THRESHOLD
  }

  public record Check(Run run, Residual residual) {}

  private static final Fact<Check> CHECK_COMPLETED =
      new Fact<>(
          new Phrase(
              "jälgimisvahe kontroll lõpeb",
              "jälgimisvahe kontroll jääb puuduvate hindade või vea tõttu pooleli",
              "pooleli jäänud kontrollist teavitatakse Slackis, aruanne edastatakse"),
          check -> check.run() == COMPLETED);

  private static final Fact<Check> MODEL_PORTFOLIO_RESIDUAL_AT_THRESHOLD =
      new Fact<>(
          new Phrase(
              "mudelportfelli NAV-jääk ulatub lävendini",
              "mudelportfelli NAV-jääk jääb alla lävendi",
              "NAV-jääk on fondi tootluse erinevus päeva alguse positsioonide põhjal oodatud"
                  + " tootlusest",
              "lävend on parameeter TRACKING_BREACH_THRESHOLD andmebaasist",
              "kui päeva alguse positsioonid puuduvad, NAV-jääki ei hinnata ja see loetakse"
                  + " lävendist väiksemaks",
              "jälgimisvahe lävendi ületamine aruannet ei peata, seda käsitletakse p 11.8"
                  + " korras"),
          check -> check.residual() == AT_THRESHOLD);

  public static final Rule<Check> SISEKORD_4_P_11_11_3 =
      new Rule<>(
          "11.11.3",
          "Puhasväärtuse aruanne peatatakse sisemiseks ülevaatuseks",
          and(CHECK_COMPLETED, MODEL_PORTFOLIO_RESIDUAL_AT_THRESHOLD),
          List.of(
              applies("NAV-jääk ulatub lävendini", new Check(COMPLETED, AT_THRESHOLD)),
              doesNotApply("NAV-jääk jääb alla lävendi", new Check(COMPLETED, BELOW_THRESHOLD)),
              doesNotApply(
                  "hinnad puuduvad, kuigi osaline tulemus ulatub lävendini",
                  new Check(INCOMPLETE_PRICES, AT_THRESHOLD)),
              doesNotApply("kontroll katkeb veaga", new Check(FAILED, BELOW_THRESHOLD))));

  private NavReportHold() {}
}
