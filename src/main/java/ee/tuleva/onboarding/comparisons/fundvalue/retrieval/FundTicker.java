package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FundTicker {
  ISHARES_USA_ESG_SCREENED(
      "SGAS.DE", "SGAS.XETRA", "IE00BFNM3G45", "iShares MSCI USA ESG Screened"),
  ISHARES_EUROPE_ESG_SCREENED(
      "SLMC.DE", "SLMC.XETRA", "IE00BFNM3D14", "iShares MSCI Europe ESG Screened"),
  ISHARES_JAPAN_ESG_SCREENED(
      "SGAJ.DE", "SGAJ.XETRA", "IE00BFNM3L97", "iShares MSCI Japan ESG Screened"),
  INVESCO_EMERGING_MARKETS_ESG(
      "ESGM.DE", "ESGM.XETRA", "IE00BMDBMY19", "Invesco MSCI Emerging Markets ESG"),
  XTRACKERS_USA_ESG_SCREENED(
      "XRSM.DE", "XRSM.XETRA", "IE00BJZ2DC62", "Xtrackers MSCI USA ESG Screened"),
  XTRACKERS_CANADA_ESG_SCREENED(
      "D5BH.DE", "D5BH.XETRA", "LU0476289540", "Xtrackers MSCI Canada ESG Screened"),
  VANGUARD_NORTH_AMERICA_ALL_CAP(
      "V3YA.DE", "V3YA.XETRA", "IE000O58J820", "Vanguard ESG North America All Cap"),
  BNP_EUROPE_ESG_FILTERED(
      "EEUX.DE", "EEUX.XETRA", "LU1291099718", "BNP Paribas Easy MSCI Europe ESG Filtered"),
  BNP_PACIFIC_EX_JAPAN_ESG(
      "PAC.DE", "PAC.XETRA", "LU1291106356", "BNP Paribas Easy MSCI Pacific ex Japan ESG"),
  BNP_JAPAN_ESG_FILTERED(
      "EJAP.DE", "EJAP.XETRA", "LU1291102447", "BNP Paribas Easy MSCI Japan ESG Filtered"),
  AMUNDI_USA_SCREENED("USAS.PA", "USAS.PA.EODHD", "IE000F60HVH9", "Amundi MSCI USA Screened"),
  ISHARES_DEVELOPED_WORLD_ESG_SCREENED(
      "0P000152G5.F",
      "IE00BFG1TM61.EUFUND",
      "IE00BFG1TM61",
      "iShares Developed World ESG Screened Index Fund"),
  ISHARES_EURO_GOVERNMENT_BOND(
      "0P00006OK2.F",
      "IE0031080751.EUFUND",
      "IE0031080751",
      "iShares Euro Government Bond Index Fund"),
  CCF_DEVELOPED_WORLD_SCREENED(
      "0P0001N0Z0.F",
      "IE0009FT4LX4.EUFUND",
      "IE0009FT4LX4",
      "CCF Developed World Screened Index Fund"),
  ISHARES_EMERGING_MARKETS_SCREENED(
      "0P0001MGOG.F",
      "IE00BKPTWY98.EUFUND",
      "IE00BKPTWY98",
      "iShares Emerging Markets Screened Equity Index Fund"),
  ISHARES_EURO_AGGREGATE_BOND(
      "0P0000YXER.F",
      "LU0826455353.EUFUND",
      "LU0826455353",
      "iShares Euro Aggregate Bond Index Fund"),
  ISHARES_GLOBAL_GOVERNMENT_BOND(
      "0P0001A3RC.F",
      "LU0839970364.EUFUND",
      "LU0839970364",
      "iShares Global Government Bond Index Fund"),
  ISHARES_EURO_CREDIT_BOND(
      "0P0000STQT.F", "IE0005032192.EUFUND", "IE0005032192", "iShares Euro Credit Bond Index Fund");

  private final String yahooTicker;
  private final String eodhdTicker;
  private final String isin;
  private final String displayName;

  public static List<String> getYahooTickers() {
    return Arrays.stream(values()).map(FundTicker::getYahooTicker).toList();
  }

  public static List<String> getEodhdTickers() {
    return Arrays.stream(values()).map(FundTicker::getEodhdTicker).toList();
  }

  public static Optional<FundTicker> findByIsin(String isin) {
    return Arrays.stream(values()).filter(ticker -> ticker.getIsin().equals(isin)).findFirst();
  }
}
