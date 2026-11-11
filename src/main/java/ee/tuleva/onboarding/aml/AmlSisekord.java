package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.MandateAmlGate.SISEKORD_13_P_4_1_7;

import ee.tuleva.onboarding.rulebook.Sisekord;
import java.time.LocalDate;
import java.util.List;

public final class AmlSisekord {

  public static final Sisekord SISEKORD_13 =
      new Sisekord(
          13,
          "Rahapesu ja terrorismi rahastamise tõkestamise ning sanktsioonide rakendamise sisekord",
          "1.1",
          LocalDate.of(2026, 6, 1),
          List.of(SISEKORD_13_P_4_1_7));

  private AmlSisekord() {}
}
