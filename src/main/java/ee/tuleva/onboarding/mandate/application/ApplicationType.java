package ee.tuleva.onboarding.mandate.application;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ApplicationType {
  TRANSFER("Vahetamise avaldus"), // 2nd and 3rd pillar
  SELECTION("Valikuavaldus"), // 2nd and 3rd pillar
  EARLY_WITHDRAWAL("Raha väljavõtmise avaldus"), // 2nd pillar
  WITHDRAWAL("Ühekordse väljamakse avaldus"), // 2nd pillar
  CANCELLATION("Avalduse tühistamise avaldus"), // 2nd pillar, to cancel EARLY_WITHDRAWAL/WITHDRAWAL
  PAYMENT("Sissemakse"), // 3rd pillar contribution payment,

  RATE("Sissemaksete määra muutmine")
  ;

  public final String nameEstonian;

  public boolean isWithdrawal() {
    return this == WITHDRAWAL || this == EARLY_WITHDRAWAL;
  }

  public boolean isTransfer() {
    return this == TRANSFER;
  }
}
