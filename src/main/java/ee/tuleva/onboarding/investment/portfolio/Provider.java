package ee.tuleva.onboarding.investment.portfolio;

import static ee.tuleva.onboarding.investment.calendar.Domicile.FRANCE;
import static ee.tuleva.onboarding.investment.calendar.Domicile.IRELAND;
import static ee.tuleva.onboarding.investment.calendar.Domicile.LUXEMBOURG;

import ee.tuleva.onboarding.investment.calendar.Domicile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Provider {
  ISHARES(IRELAND),
  CCF(FRANCE),
  INVESCO(IRELAND),
  XTRACKERS(IRELAND),
  AMUNDI(LUXEMBOURG),
  VANGUARD(IRELAND),
  BNP_PARIBAS(LUXEMBOURG);

  private final Domicile domicile;
}
