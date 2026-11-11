package ee.tuleva.onboarding.investment;

import static ee.tuleva.onboarding.investment.transaction.CashKnownToArrive.SISEKORD_4_P_2_5_3;

import ee.tuleva.onboarding.rulebook.Sisekord;
import java.time.LocalDate;
import java.util.List;

public final class InvestmentSisekord {

  public static final Sisekord SISEKORD_4 =
      new Sisekord(
          4,
          "Investeerimistegevuse ja riskijuhtimise sisekord",
          "1.4",
          LocalDate.of(2026, 9, 30),
          List.of(SISEKORD_4_P_2_5_3));

  private InvestmentSisekord() {}
}
