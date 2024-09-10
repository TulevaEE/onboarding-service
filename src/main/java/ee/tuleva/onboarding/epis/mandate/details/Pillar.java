package ee.tuleva.onboarding.epis.mandate.details;

import java.util.Arrays;

public enum Pillar {
  SECOND(2),
  THIRD(3);

  private final int pillar;

  Pillar(int pillar) {
    this.pillar = pillar;
  }

  public static Pillar fromInt(int pillar) {
    return Arrays.stream(Pillar.values())
        .filter(p -> p.pillar == pillar)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid pillar: " + pillar));
  }

  public int toInt() {
    return pillar;
  }
}
