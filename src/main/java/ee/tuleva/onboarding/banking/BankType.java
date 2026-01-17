package ee.tuleva.onboarding.banking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankType {
  SWEDBANK("Swedbank"),
  SEB("SEB");

  private final String displayName;
}
