package ee.tuleva.onboarding.account

import com.google.common.collect.Lists

import static ee.tuleva.onboarding.fund.FundFixture.*

class AccountStatementFixture {

  public static List<FundBalance> activeTuleva2ndPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva2ndPillarStockFund())
          .units(123.4567)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva2ndPillarBondFund())
          .units(234.5678)
          .build()
  ]

  public static List<FundBalance> activeExternal2ndPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv2ndPillarFund())
          .units(345.6789)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva2ndPillarBondFund())
          .units(456.7890)
          .build()
  ]

  public static List<FundBalance> inactiveTuleva2ndPillarFundBalance = [
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv2ndPillarFund())
          .units(345.6789)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva2ndPillarBondFund())
          .units(234.5678)
          .build()
  ]

  public static List<FundBalance> inactiveExternal2ndPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva2ndPillarBondFund())
          .units(234.5678)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv2ndPillarFund())
          .units(345.6789)
          .build()
  ]

  public static List<FundBalance> fullyExternal2ndPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv2ndPillarFund())
          .units(345.6789)
          .build()
  ]

  public static List<FundBalance> onlyActiveTuleva2ndPillarFundBalance = [
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva2ndPillarStockFund())
          .units(123.4567)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv2ndPillarFund())
          .units(345.6789)
          .build()
  ]

  public static List<FundBalance> activeTuleva3rdPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(234.56)
          .build(),
      FundBalance.builder()
          .value(200.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(190.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(exitRestricted3rdPillarFund())
          .units(345.67)
          .build()
  ]

  public static List<FundBalance> activeExternal3rdPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv3rdPillarFund())
          .units(2343.8579)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(234.56)
          .build()
  ]

  public static List<FundBalance> pendingExternal3rdPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv3rdPillarFund())
          .units(0.0)
          .unavailableUnits(2343.8579)
          .build(),
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(0.0)
          .build()
  ]

  public static List<FundBalance> activeTuleva3rdPillarFund = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(234.56)
          .build()
  ]

  public static List<FundBalance> inactiveTuleva3rdPillarFundBalance = [
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv3rdPillarFund())
          .units(456.78)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(234.56)
          .build()
  ]

  public static List<FundBalance> inactiveExternal3rdPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(234.56)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv3rdPillarFund())
          .units(456.78)
          .build()
  ]

  public static List<FundBalance> fullyExternal3rdPillarFundBalance = [
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv3rdPillarFund())
          .units(456.78)
          .build()
  ]

  public static List<FundBalance> onlyActiveTuleva3rdPillarFundBalance = [
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(tuleva3rdPillarFund())
          .units(234.56)
          .build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(lhv3rdPillarFund())
          .units(456.78)
          .build()
  ]

}
