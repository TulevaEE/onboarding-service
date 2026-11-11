package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;

import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.rulebook.Rule;
import java.math.BigDecimal;
import java.util.List;

public final class LimitSeverity {

  public record Reading(BigDecimal actual, BigDecimal soft, BigDecimal hard) {}

  public record FreeCash(BigDecimal freeCash, BigDecimal maxFreeCash) {}

  private static final Fact<Reading> WEIGHT_ABOVE_LIMIT =
      new Fact<>(
          new Phrase(
              "fondi osakaal ületab limiiti",
              "fondi osakaal ei ületa limiiti",
              "osakaal on positsiooni turuväärtus fondi puhasväärtusest",
              "jälgimislävi ja limiit on parameetrid investment_position_limit.soft_limit_percent"
                  + " ja hard_limit_percent andmebaasist, iga ISIN-i kohta"),
          reading -> reading.actual().compareTo(reading.hard()) > 0);

  private static final Fact<Reading> WEIGHT_ABOVE_WATCH_THRESHOLD =
      new Fact<>(
          new Phrase("fondi osakaal ületab jälgimisläve", "fondi osakaal ei ületa jälgimisläve"),
          reading -> reading.actual().compareTo(reading.soft()) > 0);

  public static final Decision<Reading, BreachSeverity> SISEKORD_4_P_3_6_5 =
      new Decision<>(
          "3.6.5",
          "Investeerimisfondi osakaalu jälgimislävi ja limiit",
          List.of(
              new Branch<>(HARD, "häireteade (limiit ületatud)", WEIGHT_ABOVE_LIMIT),
              new Branch<>(SOFT, "teade (jälgimislävi ületatud)", WEIGHT_ABOVE_WATCH_THRESHOLD),
              new Branch<>(
                  OK, "korras", not(or(WEIGHT_ABOVE_LIMIT, WEIGHT_ABOVE_WATCH_THRESHOLD)))),
          List.of(
              new Case<>(
                  "osakaal 20,5%, lävi 19,65%, limiit 20%", reading("20.5", "19.65", "20"), HARD),
              new Case<>("osakaal täpselt limiidil 20%", reading("20", "19.65", "20"), SOFT),
              new Case<>("osakaal 15%", reading("15", "19.65", "20"), OK)));

  private static final Fact<Reading> CASH_BELOW_MINIMUM_RESERVE =
      new Fact<>(
          new Phrase(
              "raha arvelduskontol on alla minimaalse likviidsusreservi",
              "raha arvelduskontol ei ole alla minimaalse likviidsusreservi",
              "optimaalne ja minimaalne reserv on parameetrid investment_fund_limit.reserve_soft"
                  + " ja reserve_hard andmebaasist",
              "kui fondil reservi parameetreid ei ole, kontrolli ei tehta"),
          reading -> reading.actual().compareTo(reading.hard()) < 0);

  private static final Fact<Reading> CASH_BELOW_OPTIMAL_RESERVE =
      new Fact<>(
          new Phrase(
              "raha arvelduskontol on alla optimaalse likviidsusreservi",
              "raha arvelduskontol ei ole alla optimaalse likviidsusreservi"),
          reading -> reading.actual().compareTo(reading.soft()) < 0);

  public static final Decision<Reading, BreachSeverity> SISEKORD_4_P_9_5 =
      new Decision<>(
          "9.5",
          "Optimaalse ja minimaalse likviidsusreservi kontroll",
          List.of(
              new Branch<>(HARD, "häireteade", CASH_BELOW_MINIMUM_RESERVE),
              new Branch<>(SOFT, "teade", CASH_BELOW_OPTIMAL_RESERVE),
              new Branch<>(
                  OK, "korras", not(or(CASH_BELOW_MINIMUM_RESERVE, CASH_BELOW_OPTIMAL_RESERVE)))),
          List.of(
              new Case<>(
                  "raha 40 000, reserv 100 000 / 50 000",
                  reading("40000", "100000", "50000"),
                  HARD),
              new Case<>(
                  "raha 80 000, reserv 100 000 / 50 000",
                  reading("80000", "100000", "50000"),
                  SOFT),
              new Case<>(
                  "raha täpselt optimaalse reservi suurune",
                  reading("100000", "100000", "50000"),
                  OK)));

  private static final Fact<Reading> OWNERSHIP_AT_LIMIT =
      new Fact<>(
          new Phrase(
              "osalus sihtfondis ulatub limiidini",
              "osalus sihtfondis jääb alla limiidi",
              "osalus on Täiendava Kogumisfondi positsiooni väärtus sihtfondi mahust",
              "jälgimislävi ja limiit on parameetrid investment_ownership_limit andmebaasist",
              "kontroll tehakse kord kuus kuu lõpu seisuga"),
          reading -> reading.actual().compareTo(reading.hard()) >= 0);

  private static final Fact<Reading> OWNERSHIP_ABOVE_WATCH_THRESHOLD =
      new Fact<>(
          new Phrase(
              "osalus sihtfondis ületab jälgimisläve", "osalus sihtfondis ei ületa jälgimisläve"),
          reading -> reading.actual().compareTo(reading.soft()) > 0);

  public static final Decision<Reading, BreachSeverity> SISEKORD_4_P_2_4_3_11 =
      new Decision<>(
          "2.4.3.11",
          "Täiendava Kogumisfondi osalus ühes investeerimisfondis",
          List.of(
              new Branch<>(HARD, "häireteade", OWNERSHIP_AT_LIMIT),
              new Branch<>(SOFT, "teade", OWNERSHIP_ABOVE_WATCH_THRESHOLD),
              new Branch<>(
                  OK, "korras", not(or(OWNERSHIP_AT_LIMIT, OWNERSHIP_ABOVE_WATCH_THRESHOLD)))),
          List.of(
              new Case<>("osalus täpselt 25%", reading("25", "20", "25"), HARD),
              new Case<>("osalus 22%", reading("22", "20", "25"), SOFT),
              new Case<>("osalus 10%", reading("10", "20", "25"), OK)));

  private static final Fact<FreeCash> FREE_CASH_ABOVE_MAXIMUM =
      new Fact<>(
          new Phrase(
              "vaba raha ületab lubatud ülempiiri",
              "vaba raha ei ületa lubatud ülempiiri",
              "vaba raha on raha arvelduskontol, millele liidetakse kohustused (negatiivsed),"
                  + " millest lahutatakse ootel tehingute rahamõju ja optimaalne likviidsusreserv",
              "ülempiir on parameeter investment_fund_limit.max_free_cash andmebaasist",
              "ülempiiri ületamine salvestatakse, kuid Slacki teadet ei saadeta"),
          freeCash -> freeCash.freeCash().compareTo(freeCash.maxFreeCash()) > 0);

  public static final Rule<FreeCash> SISEKORD_4_P_3_6_4_1 =
      new Rule<>(
          "3.6.4.1",
          "Optimaalset likviidsusreservi ületav vaba raha",
          FREE_CASH_ABOVE_MAXIMUM,
          List.of(
              applies(
                  "vaba raha 120 000, ülempiir 100 000",
                  new FreeCash(new BigDecimal("120000"), new BigDecimal("100000"))),
              doesNotApply(
                  "vaba raha täpselt ülempiiril",
                  new FreeCash(new BigDecimal("100000"), new BigDecimal("100000")))));

  private LimitSeverity() {}

  private static Reading reading(String actual, String soft, String hard) {
    return new Reading(new BigDecimal(actual), new BigDecimal(soft), new BigDecimal(hard));
  }
}
