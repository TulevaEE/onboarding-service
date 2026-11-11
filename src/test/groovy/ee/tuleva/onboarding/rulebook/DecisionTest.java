package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.rulebook.Condition.or;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionTest {

  enum Risk {
    NONE,
    LOW,
    MEDIUM,
    HIGH
  }

  enum Status {
    COMPLETED,
    PENDING,
    REJECTED
  }

  record Applicant(Risk risk) {}

  static final Fact<Applicant> NO_RISK =
      new Fact<>(
          new Phrase(
              "riskitase puudub", "riskitase on määratud", "riskitase on KYC kontrolli tulemus"),
          applicant -> applicant.risk() == Risk.NONE);

  static final Fact<Applicant> LOW_RISK =
      new Fact<>(
          new Phrase("riskitase on madal", "riskitase ei ole madal"),
          applicant -> applicant.risk() == Risk.LOW);

  static final Fact<Applicant> MEDIUM_RISK =
      new Fact<>(
          new Phrase("riskitase on keskmine", "riskitase ei ole keskmine"),
          applicant -> applicant.risk() == Risk.MEDIUM);

  static final Fact<Applicant> HIGH_RISK =
      new Fact<>(
          new Phrase("riskitase on kõrge", "riskitase ei ole kõrge"),
          applicant -> applicant.risk() == Risk.HIGH);

  static final Decision<Applicant, Status> ONBOARDING =
      new Decision<>(
          "9.3",
          "Liitumise staatus riskitaseme järgi",
          List.of(
              new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", or(NO_RISK, LOW_RISK)),
              new Branch<>(Status.PENDING, "liitumine ootab ülevaatust", MEDIUM_RISK),
              new Branch<>(Status.REJECTED, "liitumine on tagasi lükatud", HIGH_RISK)),
          List.of(
              new Case<>("madala riskiga isik", new Applicant(Risk.LOW), Status.COMPLETED),
              new Case<>("keskmise riskiga isik", new Applicant(Risk.MEDIUM), Status.PENDING),
              new Case<>("kõrge riskiga isik", new Applicant(Risk.HIGH), Status.REJECTED)));

  @Test
  void theFirstBranchThatHoldsDecidesTheOutcome() {
    assertThat(ONBOARDING.decide(new Applicant(Risk.NONE))).isEqualTo(Status.COMPLETED);
    assertThat(ONBOARDING.decide(new Applicant(Risk.MEDIUM))).isEqualTo(Status.PENDING);
    assertThat(ONBOARDING.decide(new Applicant(Risk.HIGH))).isEqualTo(Status.REJECTED);
  }

  @Test
  void aContextNoBranchDecidesIsRefused() {
    var partial =
        new Decision<>(
            "9.3",
            "Liitumise staatus riskitaseme järgi",
            List.of(
                new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", LOW_RISK),
                new Branch<>(Status.REJECTED, "liitumine on tagasi lükatud", HIGH_RISK)),
            List.of(
                new Case<>("madala riskiga isik", new Applicant(Risk.LOW), Status.COMPLETED),
                new Case<>("kõrge riskiga isik", new Applicant(Risk.HIGH), Status.REJECTED)));

    assertThatThrownBy(() -> partial.decide(new Applicant(Risk.MEDIUM)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aDecisionWithABranchNoExampleReachesIsRefused() {
    assertThatThrownBy(
            () ->
                new Decision<>(
                    "9.3",
                    "Liitumise staatus riskitaseme järgi",
                    List.of(
                        new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", LOW_RISK),
                        new Branch<>(Status.REJECTED, "liitumine on tagasi lükatud", HIGH_RISK)),
                    List.of(
                        new Case<>(
                            "madala riskiga isik", new Applicant(Risk.LOW), Status.COMPLETED))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void twoBranchesMayShareAnOutcomeWhenAnExampleReachesEach() {
    var shared =
        new Decision<>(
            "9.3",
            "Liitumise staatus riskitaseme järgi",
            List.of(
                new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", NO_RISK),
                new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", LOW_RISK),
                new Branch<>(Status.REJECTED, "liitumine on tagasi lükatud", HIGH_RISK)),
            List.of(
                new Case<>("riskita isik", new Applicant(Risk.NONE), Status.COMPLETED),
                new Case<>("madala riskiga isik", new Applicant(Risk.LOW), Status.COMPLETED),
                new Case<>("kõrge riskiga isik", new Applicant(Risk.HIGH), Status.REJECTED)));

    assertThat(shared.decide(new Applicant(Risk.LOW))).isEqualTo(Status.COMPLETED);
  }

  @Test
  void aBranchOnlyAnEarlierBranchsExamplesShareTheOutcomeOfIsStillRefused() {
    assertThatThrownBy(
            () ->
                new Decision<>(
                    "9.3",
                    "Liitumise staatus riskitaseme järgi",
                    List.of(
                        new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", NO_RISK),
                        new Branch<>(Status.COMPLETED, "liitumine on lõpetatud", LOW_RISK),
                        new Branch<>(Status.REJECTED, "liitumine on tagasi lükatud", HIGH_RISK)),
                    List.of(
                        new Case<>("riskita isik", new Applicant(Risk.NONE), Status.COMPLETED),
                        new Case<>(
                            "kõrge riskiga isik", new Applicant(Risk.HIGH), Status.REJECTED))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aDecisionWritesEachOutcomeWithItsConditionAndEachExampleWithItsReason() {
    assertThat(ONBOARDING.markdown())
        .isEqualTo(
            List.of(
                "## p 9.3 – Liitumise staatus riskitaseme järgi",
                "Tulemus on esimene, mille tingimus kehtib:",
                "- liitumine on lõpetatud, kui riskitase puudub või riskitase on madal;\n"
                    + "- liitumine ootab ülevaatust, kui riskitase on keskmine;\n"
                    + "- liitumine on tagasi lükatud, kui riskitase on kõrge.",
                "Andmed: riskitase on KYC kontrolli tulemus.",
                "Näide: madala riskiga isik – liitumine on lõpetatud, sest riskitase on madal.",
                "Näide: keskmise riskiga isik – liitumine ootab ülevaatust, sest riskitase on"
                    + " määratud, riskitase ei ole madal ja riskitase on keskmine.",
                "Näide: kõrge riskiga isik – liitumine on tagasi lükatud, sest riskitase on"
                    + " määratud, riskitase ei ole madal, riskitase ei ole keskmine ja riskitase"
                    + " on kõrge."));
  }

  @Test
  void anExampleDecidesAsLabelledOnlyWhenTheBranchesReachItsOutcome() {
    var mislabelled =
        new Decision<>(
            "9.3",
            "Liitumise staatus riskitaseme järgi",
            ONBOARDING.branches(),
            List.of(
                new Case<>("madala riskiga isik", new Applicant(Risk.LOW), Status.COMPLETED),
                new Case<>("keskmise riskiga isik", new Applicant(Risk.MEDIUM), Status.PENDING),
                new Case<>("kõrge riskiga isik", new Applicant(Risk.HIGH), Status.REJECTED),
                new Case<>("riskita isik", new Applicant(Risk.NONE), Status.PENDING)));

    assertThat(mislabelled.exampleNames())
        .containsExactly(
            "madala riskiga isik", "keskmise riskiga isik", "kõrge riskiga isik", "riskita isik");
    assertThat(mislabelled.decidesAsLabelled("madala riskiga isik")).isTrue();
    assertThat(mislabelled.decidesAsLabelled("riskita isik")).isFalse();
  }
}
