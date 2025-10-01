package ee.tuleva.onboarding.mandate.application;

import java.util.EnumSet;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ApplicationType {
  TRANSFER("Vahetamise avaldus"), // 2nd and 3rd pillar
  SELECTION("Valikuavaldus"), // 2nd and 3rd pillar
  EARLY_WITHDRAWAL("Raha väljavõtmise avaldus"), // 2nd pillar
  WITHDRAWAL("Ühekordse väljamakse avaldus"), // 2nd pillar
  CANCELLATION("Avalduse tühistamise avaldus"), // 2nd pillar, to cancel EARLY_WITHDRAWAL/WITHDRAWAL
  PAYMENT("Sissemakse"), // 3rd pillar contribution payment,
  PAYMENT_RATE("Sissemaksete määra muutmine"), // 2nd pillar payment rate change
  FUND_PENSION_OPENING("Fondipensioni avamise avaldus"),
  FUND_PENSION_OPENING_THIRD_PILLAR("Täiendava fondipensioni avamise avaldus"),
  PARTIAL_WITHDRAWAL("Osalise väljamakse avaldus II sambast"),
  WITHDRAWAL_THIRD_PILLAR("Väljamakse avaldus vabatahtlikust pensionifondist"),
  SAVING_FUND_PAYMENT("Täiendava kogumisfondi sissemakse"),
  ;

  public final String nameEstonian;

  public boolean isWithdrawal() {
    return EnumSet.of(WITHDRAWAL, EARLY_WITHDRAWAL, PARTIAL_WITHDRAWAL, WITHDRAWAL_THIRD_PILLAR)
        .contains(this);
  }

  public boolean isFundPensionOpening() {
    return EnumSet.of(FUND_PENSION_OPENING, FUND_PENSION_OPENING_THIRD_PILLAR).contains(this);
  }

  public boolean isTransfer() {
    return this == TRANSFER;
  }

  public boolean isPaymentRate() {
    return this == PAYMENT_RATE;
  }
}
