package ee.tuleva.onboarding.investment;

import java.time.LocalDate;

public final class CashKnownToArrive {

  public record Calculation(LocalDate asOfDate, LocalDate madeOn) {}

  private static final LocalDate SISEKORD_4_P_2_5_3_CASH_KNOWN_TO_ARRIVE_INVESTABLE_FROM =
      LocalDate.of(2027, 1, 1);

  private CashKnownToArrive() {}

  public static boolean investableBeforeItArrives(Calculation calculation) {
    return isFromTheStart(calculation.asOfDate()) && isFromTheStart(calculation.madeOn());
  }

  private static boolean isFromTheStart(LocalDate day) {
    return !day.isBefore(SISEKORD_4_P_2_5_3_CASH_KNOWN_TO_ARRIVE_INVESTABLE_FROM);
  }
}
