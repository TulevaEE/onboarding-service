package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;

import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class TkfOnboardingDecision {

  public record Assessment(@Nullable SavingsFundOnboardingStatus current, RiskLevel risk) {}

  private static final Fact<Assessment> ALREADY_COMPLETED =
      new Fact<>(
          new Phrase(
              "isiku liitumine on juba lõpetatud",
              "isiku liitumine ei ole lõpetatud",
              "riskitase on isiku viimase KYC kontrolli tulemus"),
          assessment -> assessment.current() == COMPLETED);

  private static final Fact<Assessment> NO_RISK_LEVEL =
      riskIs(NONE, "riskitase puudub", "riskitase on määratud");

  private static final Fact<Assessment> LOW_RISK =
      riskIs(LOW, "riskitase on madal", "riskitase ei ole madal");

  private static final Fact<Assessment> MEDIUM_RISK =
      riskIs(MEDIUM, "riskitase on keskmine", "riskitase ei ole keskmine");

  private static final Fact<Assessment> HIGH_RISK =
      riskIs(HIGH, "riskitase on kõrge", "riskitase ei ole kõrge");

  public static final Decision<Assessment, SavingsFundOnboardingStatus> SISEKORD_13_P_9_3_3 =
      new Decision<>(
          "9.3.3",
          "Täiendava Kogumisfondiga liitumine KYC riskitaseme järgi",
          List.of(
              new Branch<>(COMPLETED, "liitumine jääb lõpetatuks", ALREADY_COMPLETED),
              new Branch<>(COMPLETED, "liitumine lõpetatakse", or(NO_RISK_LEVEL, LOW_RISK)),
              new Branch<>(PENDING, "liitumine jääb ootele", MEDIUM_RISK),
              new Branch<>(REJECTED, "liitumine lükatakse tagasi", HIGH_RISK)),
          List.of(
              new Case<>("uus madala riskiga isik", new Assessment(null, LOW), COMPLETED),
              new Case<>(
                  "ootel isik, kelle riskitase puudub", new Assessment(PENDING, NONE), COMPLETED),
              new Case<>("uus keskmise riskiga isik", new Assessment(null, MEDIUM), PENDING),
              new Case<>("ootel kõrge riskiga isik", new Assessment(PENDING, HIGH), REJECTED),
              new Case<>(
                  "lõpetatud liitumisega isik, kelle riskitase tõuseb kõrgeks",
                  new Assessment(COMPLETED, HIGH),
                  COMPLETED),
              new Case<>(
                  "tagasi lükatud isik, kelle riskitase langeb madalaks",
                  new Assessment(REJECTED, LOW),
                  COMPLETED)));

  private TkfOnboardingDecision() {}

  private static Fact<Assessment> riskIs(RiskLevel level, String holds, String fails) {
    return new Fact<>(new Phrase(holds, fails), assessment -> assessment.risk() == level);
  }
}
