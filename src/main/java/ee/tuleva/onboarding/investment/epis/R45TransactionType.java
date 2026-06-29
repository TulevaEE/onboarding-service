package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.INFLOW;
import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.OUTFLOW;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum R45TransactionType {
  SUB(INFLOW),
  RED(OUTFLOW),
  SWI(OUTFLOW),
  SWS(INFLOW),
  COR(OUTFLOW),
  PAAV(OUTFLOW),
  RES(INFLOW);

  public enum Direction {
    INFLOW,
    OUTFLOW
  }

  private final Direction direction;

  public static Optional<R45TransactionType> find(String code) {
    return Arrays.stream(values()).filter(type -> type.name().equalsIgnoreCase(code)).findFirst();
  }
}
