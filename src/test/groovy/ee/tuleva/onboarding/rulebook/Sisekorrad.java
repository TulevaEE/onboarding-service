package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.aml.MandateAmlGate.SISEKORD_13_P_4_1_7;
import static ee.tuleva.onboarding.banking.seb.reconciliation.BankReconciliation.SISEKORD_16_P_1_4_1;
import static ee.tuleva.onboarding.investment.CashKnownToArrive.SISEKORD_4_P_2_5_3;
import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.SISEKORD_4_P_2_4_3_11;
import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.SISEKORD_4_P_3_6_4_1;
import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.SISEKORD_4_P_3_6_5;
import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.SISEKORD_4_P_9_5;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.SISEKORD_4_P_11_11_3;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.SISEKORD_4_P_11_8;
import static ee.tuleva.onboarding.savings.NavPublication.SISEKORD_4_P_11_3_3;
import static ee.tuleva.onboarding.savings.TkfOnboardingDecision.SISEKORD_13_P_9_3_3;
import static ee.tuleva.onboarding.savings.TradeDay.SISEKORD_16_P_1_1_3;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.SISEKORD_4_P_11_3_1;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.SISEKORD_13_P_10_8;

import java.time.LocalDate;
import java.util.List;

final class Sisekorrad {

  static final Sisekord SISEKORD_4 =
      new Sisekord(
          4,
          "Investeerimistegevuse ja riskijuhtimise sisekord",
          "1.4",
          LocalDate.of(2026, 9, 30),
          List.of(
              SISEKORD_4_P_2_4_3_11,
              SISEKORD_4_P_2_5_3,
              SISEKORD_4_P_3_6_4_1,
              SISEKORD_4_P_3_6_5,
              SISEKORD_4_P_9_5,
              SISEKORD_4_P_11_3_1,
              SISEKORD_4_P_11_3_3,
              SISEKORD_4_P_11_8,
              SISEKORD_4_P_11_11_3));

  static final Sisekord SISEKORD_13 =
      new Sisekord(
          13,
          "Rahapesu ja terrorismi rahastamise tõkestamise ning sanktsioonide rakendamise sisekord",
          "1.1",
          LocalDate.of(2026, 6, 1),
          List.of(SISEKORD_13_P_4_1_7, SISEKORD_13_P_9_3_3, SISEKORD_13_P_10_8));

  static final Sisekord SISEKORD_16 =
      new Sisekord(
          16,
          "Täiendava Kogumisfondi Osakute registri pidamise sisekord",
          "1.1",
          LocalDate.of(2026, 8, 31),
          List.of(SISEKORD_16_P_1_1_3, SISEKORD_16_P_1_4_1));

  private Sisekorrad() {}
}
