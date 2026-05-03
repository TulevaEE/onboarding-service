package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum FundTicker {
  ISHARES_USA_ESG_SCREENED(
      "SGAS.DE",
      "SGAS.XETRA",
      "IE00BFNM3G45",
      "iShares MSCI USA Screened UCITS ETF",
      "SGAS",
      null,
      BenchmarkCategory.EQUITY_DM),
  ISHARES_EUROPE_ESG_SCREENED(
      "SLMC.DE",
      "SLMC.XETRA",
      "IE00BFNM3D14",
      "iShares MSCI Europe Screened UCITS ETF",
      "SLMC",
      null,
      BenchmarkCategory.EQUITY_DM),
  ISHARES_JAPAN_ESG_SCREENED(
      "SGAJ.DE",
      "SGAJ.XETRA",
      "IE00BFNM3L97",
      "iShares MSCI Japan Screened UCITS ETF",
      "SGAJ",
      null,
      BenchmarkCategory.EQUITY_DM),
  INVESCO_EMERGING_MARKETS_ESG(
      "ESGM.DE",
      "ESGM.XETRA",
      "IE00BMDBMY19",
      "Invesco MSCI Emerging Markets Universal Screened UCITS ETF Acc",
      "ESGM",
      null,
      BenchmarkCategory.EQUITY_EM),
  XTRACKERS_USA_ESG_SCREENED(
      "XRSM.DE",
      "XRSM.XETRA",
      "IE00BJZ2DC62",
      "Xtrackers MSCI USA Screened UCITS ETF",
      "XRSM",
      null,
      BenchmarkCategory.EQUITY_DM),
  XTRACKERS_CANADA_ESG_SCREENED(
      "D5BH.DE",
      "D5BH.XETRA",
      "LU0476289540",
      "Xtrackers MSCI Canada Screened UCITS ETF",
      "D5BH",
      null,
      BenchmarkCategory.EQUITY_DM),
  VANGUARD_NORTH_AMERICA_ALL_CAP(
      "V3YA.DE",
      "V3YA.XETRA",
      "IE000O58J820",
      "Vanguard ESG North America All Cap UCITS ETF",
      "V3YA",
      null,
      BenchmarkCategory.EQUITY_DM),
  BNP_EUROPE_ESG_FILTERED(
      "EEUX.DE",
      "EEUX.XETRA",
      "LU1291099718",
      "BNP Paribas Easy MSCI EUROPE MIN TE UCITS ETF",
      "EEUX",
      null,
      BenchmarkCategory.EQUITY_DM),
  BNP_PACIFIC_EX_JAPAN_ESG(
      "PAC.DE",
      "PAC.XETRA",
      "LU1291106356",
      "BNP Paribas Easy MSCI Pacific ex Japan Min TE UCITS ETF",
      "PAC",
      null,
      BenchmarkCategory.EQUITY_DM),
  BNP_JAPAN_ESG_FILTERED(
      "EJAP.DE",
      "EJAP.XETRA",
      "LU1291102447",
      "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
      "EJAP",
      null,
      BenchmarkCategory.EQUITY_DM),
  AMUNDI_USA_SCREENED(
      "USAS.PA",
      "USAS.PA.EODHD",
      "IE000F60HVH9",
      "ICAV Amundi MSCI USA Screened UCITS ETF",
      "USAS",
      null,
      BenchmarkCategory.EQUITY_DM),
  ISHARES_DEVELOPED_WORLD_ESG_SCREENED(
      "0P000152G5.F",
      "IE00BFG1TM61.EUFUND",
      "IE00BFG1TM61",
      "iShares Developed World Screened Index Fund",
      "BDWTEIA",
      "270890",
      "0P000152G5",
      BenchmarkCategory.EQUITY_DM),
  CCF_DEVELOPED_WORLD_SCREENED(
      "0P0001N0Z0.F",
      "IE0009FT4LX4.EUFUND",
      "IE0009FT4LX4",
      "CCF Developed World Screened Index Fund",
      "BLESIXE",
      "320377",
      "0P0001N0Z0",
      BenchmarkCategory.EQUITY_DM),
  ISHARES_EMERGING_MARKETS_SCREENED(
      "0P0001MGOG.F",
      "IE00BKPTWY98.EUFUND",
      "IE00BKPTWY98",
      "iShares Emerging Market Screened Equity Index Fund",
      "BEMEFLE",
      "316651",
      "0P0001MGOG",
      BenchmarkCategory.EQUITY_EM),
  ISHARES_EURO_AGGREGATE_BOND(
      "0P0000YXER.F",
      "LU0826455353.EUFUND",
      "LU0826455353",
      "iShares Euro Aggregate Bond Index Fund",
      "BGIEAX2",
      "254318",
      "0P0000YXER",
      BenchmarkCategory.BOND_EURO),
  ISHARES_EURO_GOVERNMENT_BOND(
      "0P00006OK2.F",
      "IE0031080751.EUFUND",
      "IE0031080751",
      "iShares Euro Government Bond Index Fund",
      "BARGVBI",
      "229062",
      "0P00006OK2",
      BenchmarkCategory.BOND_EURO),
  ISHARES_GLOBAL_GOVERNMENT_BOND(
      "0P0001A3RC.F",
      "LU0839970364.EUFUND",
      "LU0839970364",
      "iShares Global Government Bond Index Fund",
      "BGGGX2E",
      "287052",
      "0P0001A3RC",
      BenchmarkCategory.BOND_GLOBAL),
  ISHARES_EURO_CREDIT_BOND(
      "0P0000STQT.F",
      "IE0005032192.EUFUND",
      "IE0005032192",
      "iShares Euro Credit Bond Index Fund",
      "BAREUBD",
      "229055",
      "0P0000STQT",
      BenchmarkCategory.BOND_EURO),

  // Benchmark proxy ETFs (not held in portfolios — used for BENCHMARK_MODEL comparison)
  ISHARES_CORE_MSCI_WORLD(
      "IWDA.DE", "IWDA.XETRA", "IE00B4L5Y983", "iShares Core MSCI World UCITS ETF", "IWDA", null),
  ISHARES_MSCI_EM(
      "EUNM.DE", "EUNM.XETRA", "IE00B4L5YC18", "iShares MSCI EM UCITS ETF", "EUNM", null),
  ISHARES_EURO_AGG_BOND_ETF(
      "IEAG.DE",
      "IEAG.XETRA",
      "IE00B3DKXQ41",
      "iShares Euro Aggregate Bond UCITS ETF",
      "IEAG",
      null),
  ISHARES_GLOBAL_AGG_BOND_ETF(
      "AGGH.DE",
      "AGGH.XETRA",
      "IE00BDBRDM35",
      "iShares Global Aggregate Bond UCITS ETF",
      "AGGH",
      null);

  private final String yahooTicker;
  private final String eodhdTicker;
  private final String isin;
  private final String displayName;
  private final String bloombergTicker;
  private final String blackrockProductId;
  private final String morningstarId;
  private final BenchmarkCategory benchmarkCategory;

  FundTicker(
      String yahooTicker,
      String eodhdTicker,
      String isin,
      String displayName,
      String bloombergTicker,
      String blackrockProductId) {
    this(yahooTicker, eodhdTicker, isin, displayName, bloombergTicker, blackrockProductId, null);
  }

  FundTicker(
      String yahooTicker,
      String eodhdTicker,
      String isin,
      String displayName,
      String bloombergTicker,
      String blackrockProductId,
      BenchmarkCategory benchmarkCategory) {
    this(
        yahooTicker,
        eodhdTicker,
        isin,
        displayName,
        bloombergTicker,
        blackrockProductId,
        null,
        benchmarkCategory);
  }

  FundTicker(
      String yahooTicker,
      String eodhdTicker,
      String isin,
      String displayName,
      String bloombergTicker,
      String blackrockProductId,
      String morningstarId,
      BenchmarkCategory benchmarkCategory) {
    this.yahooTicker = yahooTicker;
    this.eodhdTicker = eodhdTicker;
    this.isin = isin;
    this.displayName = displayName;
    this.bloombergTicker = bloombergTicker;
    this.blackrockProductId = blackrockProductId;
    this.morningstarId = morningstarId;
    this.benchmarkCategory = benchmarkCategory;
  }

  public static List<FundTicker> getMorningstarFunds() {
    return Arrays.stream(values()).filter(ticker -> ticker.getMorningstarId() != null).toList();
  }

  public static List<String> getYahooTickers() {
    return Arrays.stream(values()).map(FundTicker::getYahooTicker).toList();
  }

  public static List<String> getEodhdTickers() {
    return Arrays.stream(values()).map(FundTicker::getEodhdTicker).toList();
  }

  public static Optional<FundTicker> findByIsin(String isin) {
    return Arrays.stream(values()).filter(ticker -> ticker.getIsin().equals(isin)).findFirst();
  }

  public static Optional<FundTicker> findByTicker(String ticker) {
    return Arrays.stream(values())
        .filter(
            fundTicker -> {
              String yahooTicker = fundTicker.getYahooTicker();
              int dotIndex = yahooTicker.indexOf('.');
              String shortTicker = dotIndex > 0 ? yahooTicker.substring(0, dotIndex) : yahooTicker;
              return shortTicker.equals(ticker);
            })
        .findFirst();
  }

  public static Optional<FundTicker> findByBloombergTicker(String bloombergTicker) {
    return Arrays.stream(values())
        .filter(fundTicker -> bloombergTicker.equals(fundTicker.getBloombergTicker()))
        .findFirst();
  }

  public static List<String> getXetraIsins() {
    return Arrays.stream(values())
        .filter(ticker -> ticker.getEodhdTicker().endsWith(".XETRA"))
        .map(FundTicker::getIsin)
        .toList();
  }

  public static List<String> getEuronextParisIsins() {
    return Arrays.stream(values())
        .filter(ticker -> ticker.getEodhdTicker().endsWith(".PA.EODHD"))
        .map(FundTicker::getIsin)
        .toList();
  }

  public Optional<String> getXetraStorageKey() {
    if (eodhdTicker.endsWith(".XETRA")) {
      return Optional.of(isin + ".XETR");
    }
    return Optional.empty();
  }

  public Optional<String> getEuronextParisStorageKey() {
    if (eodhdTicker.endsWith(".PA.EODHD")) {
      return Optional.of(isin + ".XPAR");
    }
    return Optional.empty();
  }

  public static List<FundTicker> getBlackrockFunds() {
    return Arrays.stream(values())
        .filter(ticker -> ticker.getBlackrockProductId() != null)
        .toList();
  }

  public Optional<String> getMorningstarStorageKey() {
    if (morningstarId != null) {
      return Optional.of(isin + ".MORNINGSTAR");
    }
    return Optional.empty();
  }

  public Optional<String> getBlackrockStorageKey() {
    if (blackrockProductId != null) {
      return Optional.of(isin + ".BLACKROCK");
    }
    return Optional.empty();
  }

  public enum BenchmarkCategory {
    EQUITY_DM,
    EQUITY_EM,
    BOND_EURO,
    BOND_GLOBAL
  }
}
