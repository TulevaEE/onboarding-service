package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;
import static ee.tuleva.onboarding.savings.TradeDay.Day.DAY_OFF;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.Report.MISSING;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.Report.NOT_CHECKED;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.Report.PUBLISHED;

import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.rulebook.Rule;
import ee.tuleva.onboarding.savings.TradeDay.Day;
import java.time.LocalTime;
import java.util.List;

public final class NavDeadline {

  public static final LocalTime SISEKORD_4_P_11_3_1_PILLAR_2_NAV_DEADLINE = LocalTime.of(11, 30);
  public static final LocalTime SISEKORD_4_P_11_3_2_PILLAR_3_AND_TKF_NAV_DEADLINE =
      LocalTime.of(15, 30);

  public enum Report {
    PUBLISHED,
    MISSING,
    NOT_CHECKED
  }

  public record Status(Day day, Report report) {}

  private static final Fact<Status> ON_A_WORKING_DAY =
      new Fact<>(
          new Phrase(
              "täna on tööpäev",
              "täna on laupäev, pühapäev või riigipüha",
              "tähtaeg on TUK75 ja TUK00 puhul kell "
                  + SISEKORD_4_P_11_3_1_PILLAR_2_NAV_DEADLINE
                  + ", TUV100 ja TKF100 puhul kell "
                  + SISEKORD_4_P_11_3_2_PILLAR_3_AND_TKF_NAV_DEADLINE,
              "kontroll tehakse 5 minutit pärast tähtaega; eelhoiatus saadetakse veidi pärast"
                  + " arvutuse algust samal tingimusel"),
          status -> status.day() == WORKING_DAY);

  private static final Fact<Status> REPORT_MISSING =
      new Fact<>(
          new Phrase(
              "fondi puhasväärtuse aruanne on avaldamata",
              "fondi puhasväärtuse aruande puudumist ei ole tuvastatud",
              "oodatav aruanne on eelmise tööpäeva positsioonide põhjal arvutatud puhasväärtus"),
          status -> status.report() == MISSING);

  public static final Rule<Status> SISEKORD_4_P_11_3_1 =
      new Rule<>(
          "11.3.1–11.3.3",
          "Hilinenud puhasväärtusest teavitatakse kiireloomuliselt",
          and(ON_A_WORKING_DAY, REPORT_MISSING),
          List.of(
              applies("tööpäeval tähtajaks avaldamata aruanne", new Status(WORKING_DAY, MISSING)),
              doesNotApply("tööpäeval avaldatud aruanne", new Status(WORKING_DAY, PUBLISHED)),
              doesNotApply("laupäev", new Status(DAY_OFF, NOT_CHECKED))));

  private NavDeadline() {}
}
