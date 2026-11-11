package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Onboarding.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Onboarding.INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Onboarding.NOT_CHECKED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.CLEAR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.FREEZE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.HOLD_PAYOUT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Outcome.RETRY;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.HIGH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.NOT_ASKED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk.NOT_HIGH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.NOT_REPEATED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.PEP_HIT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.SANCTION_HIT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening.UNAVAILABLE;
import static java.util.stream.Collectors.toCollection;

import ee.tuleva.onboarding.aml.ScreeningOutcome;
import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Condition;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RedemptionVerdict {

  public enum Outcome {
    RETRY,
    FREEZE,
    HOLD_PAYOUT,
    CLEAR
  }

  public enum Screening {
    CLEAR,
    PEP_HIT,
    SANCTION_HIT,
    UNAVAILABLE,
    FAILED,
    NOT_REPEATED;

    static Screening of(ScreeningOutcome outcome) {
      return switch (outcome) {
        case CLEAR -> CLEAR;
        case PEP_HIT -> PEP_HIT;
        case SANCTION_HIT -> SANCTION_HIT;
        case UNAVAILABLE -> UNAVAILABLE;
      };
    }

    boolean leavesTheRiskLevelUnasked() {
      return this == UNAVAILABLE || this == SANCTION_HIT;
    }
  }

  public enum Risk {
    HIGH,
    NOT_HIGH,
    UNAVAILABLE,
    NOT_ASKED
  }

  public enum Onboarding {
    COMPLETED,
    INCOMPLETE,
    NOT_CHECKED
  }

  public record Evidence(Screening screening, Risk risk, Onboarding onboarding) {}

  private static final Fact<Evidence> SCREENING_UNAVAILABLE =
      new Fact<>(
          new Phrase(
              "sanktsioonide ja PEP-i skriinimine ei vasta",
              "skriinimine vastab",
              "isikut skriinitakse tema elukoha- ja kodakondsusriikide järgi",
              "ettevõtet skriinitakse äriregistri andmetel uuesti, kui tema liitumine ei ole"
                  + " lõpetatud"),
          evidence -> evidence.screening() == UNAVAILABLE);

  private static final Fact<Evidence> SANCTION =
      new Fact<>(
          new Phrase("skriinimine leiab sanktsiooni", "skriinimine ei leia sanktsiooni"),
          evidence -> evidence.screening() == SANCTION_HIT);

  private static final Fact<Evidence> PEP =
      new Fact<>(
          new Phrase("skriinimine leiab, et isik on PEP", "skriinimine ei leia PEP-i"),
          evidence -> evidence.screening() == PEP_HIT);

  private static final Fact<Evidence> KYB_SCREENING_FAILED =
      new Fact<>(
          new Phrase("ettevõtte uus skriinimine katkeb veaga", "ettevõtte skriinimine ei katke"),
          evidence -> evidence.screening() == FAILED);

  private static final Fact<Evidence> ONBOARDING_INCOMPLETE =
      new Fact<>(
          new Phrase(
              "ettevõtte liitumine jääb pärast uut skriinimist lõpetamata",
              "ettevõtte liitumine ei jää lõpetamata"),
          evidence ->
              evidence.screening() == Screening.CLEAR && evidence.onboarding() == INCOMPLETE);

  private static final Fact<Evidence> HIGH_RISK =
      new Fact<>(
          new Phrase(
              "riskitase on kõrge",
              "riskitase ei ole kõrge",
              "riskitase on aml-service'i hinnang; seda küsitakse, kui skriinimine vastas ega"
                  + " leidnud sanktsiooni"),
          evidence -> evidence.risk() == HIGH);

  private static final Fact<Evidence> RISK_LEVEL_UNAVAILABLE =
      new Fact<>(
          new Phrase("riskitase ei ole kättesaadav", "riskitase on kättesaadav"),
          evidence -> evidence.risk() == Risk.UNAVAILABLE);

  private static final Map<RedemptionHoldReason, Fact<Evidence>> HOLD_REASONS =
      new EnumMap<>(
          Map.of(
              RedemptionHoldReason.SCREENING_UNAVAILABLE, SCREENING_UNAVAILABLE,
              RedemptionHoldReason.PEP, PEP,
              RedemptionHoldReason.KYB_SCREENING_FAILED, KYB_SCREENING_FAILED,
              RedemptionHoldReason.ONBOARDING_INCOMPLETE, ONBOARDING_INCOMPLETE,
              RedemptionHoldReason.HIGH_RISK, HIGH_RISK,
              RedemptionHoldReason.RISK_LEVEL_UNAVAILABLE, RISK_LEVEL_UNAVAILABLE));

  private static final Condition<Evidence> ANY_HOLD_REASON =
      or(PEP, KYB_SCREENING_FAILED, ONBOARDING_INCOMPLETE, HIGH_RISK, RISK_LEVEL_UNAVAILABLE);

  private static final String RETRY_LABEL =
      "kontrolli korratakse, tähtaja möödudes täidetakse tagasivõtmine, kuid väljamakse"
          + " peatatakse";

  public static final Decision<Evidence, Outcome> SISEKORD_13_P_10_8 =
      new Decision<>(
          "10.8",
          "Täiendava Kogumisfondi osakute tagasivõtmine hoolsusmeetmete kontrolli järgi",
          List.of(
              new Branch<>(RETRY, RETRY_LABEL, SCREENING_UNAVAILABLE),
              new Branch<>(FREEZE, "tagasivõtmine külmutatakse (p 6.3.2)", SANCTION),
              new Branch<>(
                  RETRY,
                  RETRY_LABEL,
                  and(
                      RISK_LEVEL_UNAVAILABLE,
                      not(PEP),
                      not(KYB_SCREENING_FAILED),
                      not(ONBOARDING_INCOMPLETE))),
              new Branch<>(
                  HOLD_PAYOUT,
                  "tagasivõtmine täidetakse, kuid väljamakse peatatakse",
                  ANY_HOLD_REASON),
              new Branch<>(CLEAR, "tagasivõtmine kinnitatakse", not(ANY_HOLD_REASON))),
          List.of(
              new Case<>(
                  "isik, kelle skriinimine ei vasta",
                  new Evidence(UNAVAILABLE, NOT_ASKED, NOT_CHECKED),
                  RETRY),
              new Case<>(
                  "sanktsioneeritud isik",
                  new Evidence(SANCTION_HIT, NOT_ASKED, NOT_CHECKED),
                  FREEZE),
              new Case<>(
                  "isik, kelle riskitase ei ole kättesaadav",
                  new Evidence(Screening.CLEAR, Risk.UNAVAILABLE, NOT_CHECKED),
                  RETRY),
              new Case<>(
                  "PEP, kelle riskitase ei ole kättesaadav",
                  new Evidence(PEP_HIT, Risk.UNAVAILABLE, NOT_CHECKED),
                  HOLD_PAYOUT),
              new Case<>(
                  "kõrge riskiga PEP", new Evidence(PEP_HIT, HIGH, NOT_CHECKED), HOLD_PAYOUT),
              new Case<>(
                  "liitumata ettevõte, mille uus skriinimine katkeb veaga",
                  new Evidence(FAILED, NOT_HIGH, INCOMPLETE),
                  HOLD_PAYOUT),
              new Case<>(
                  "ettevõte, mille liitumine jääb pärast uut skriinimist lõpetamata",
                  new Evidence(Screening.CLEAR, NOT_HIGH, INCOMPLETE),
                  HOLD_PAYOUT),
              new Case<>(
                  "liitunud ettevõte, mille riskitase ei ole kõrge",
                  new Evidence(NOT_REPEATED, NOT_HIGH, COMPLETED),
                  CLEAR),
              new Case<>(
                  "isik, kelle skriinimine ei leia midagi ja riskitase ei ole kõrge",
                  new Evidence(Screening.CLEAR, NOT_HIGH, NOT_CHECKED),
                  CLEAR)));

  private RedemptionVerdict() {}

  public static Set<RedemptionHoldReason> holdReasons(Evidence evidence) {
    return HOLD_REASONS.entrySet().stream()
        .filter(reason -> reason.getValue().test(evidence))
        .map(Map.Entry::getKey)
        .collect(toCollection(() -> EnumSet.noneOf(RedemptionHoldReason.class)));
  }
}
