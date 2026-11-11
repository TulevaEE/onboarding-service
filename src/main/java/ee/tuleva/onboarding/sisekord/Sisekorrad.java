package ee.tuleva.onboarding.sisekord;

import static ee.tuleva.onboarding.aml.MandateAmlGate.SISEKORD_13_P_4_1_7;
import static ee.tuleva.onboarding.investment.CashKnownToArrive.SISEKORD_4_P_2_5_3;
import static ee.tuleva.onboarding.savings.TkfOnboardingDecision.SISEKORD_13_P_9_3_3;

import ee.tuleva.onboarding.rulebook.Sisekord;
import java.time.LocalDate;
import java.util.List;

public final class Sisekorrad {

  public static final Sisekord SISEKORD_4 =
      new Sisekord(
          4,
          "Investeerimistegevuse ja riskijuhtimise sisekord",
          "1.4",
          LocalDate.of(2026, 9, 30),
          List.of(SISEKORD_4_P_2_5_3));

  public static final Sisekord SISEKORD_13 =
      new Sisekord(
          13,
          "Rahapesu ja terrorismi rahastamise tõkestamise ning sanktsioonide rakendamise sisekord",
          "1.1",
          LocalDate.of(2026, 6, 1),
          List.of(SISEKORD_13_P_4_1_7, SISEKORD_13_P_9_3_3));

  private Sisekorrad() {}
}
