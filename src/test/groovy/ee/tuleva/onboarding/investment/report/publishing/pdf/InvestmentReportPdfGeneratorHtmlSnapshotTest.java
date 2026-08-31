package ee.tuleva.onboarding.investment.report.publishing.pdf;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(SnapshotExtension.class)
class InvestmentReportPdfGeneratorHtmlSnapshotTest {

  private final ITemplateEngine templateEngine = createTemplateEngine();
  private final InvestmentReportPdfGenerator generator =
      new InvestmentReportPdfGenerator(templateEngine);
  private Expect expect;
  private Locale originalLocale;

  @BeforeEach
  void fixDefaultLocale() {
    originalLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(originalLocale);
  }

  @Test
  void multiSectionReportWithMixedNullsAndSignedChanges() {
    var context = multiSectionContext();

    var html = generator.generateHtml(context);

    expect.toMatchSnapshot(html);
  }

  @Test
  void boundaryReportWithZeroNegativeAndNullAmounts() {
    var context = boundaryContext();

    var html = generator.generateHtml(context);

    expect.toMatchSnapshot(html);
  }

  private InvestmentReportContext multiSectionContext() {
    var equityRowWithoutCostBasis =
        new InvestmentReportRow(
            "CCF Developed World",
            "BlackRock Asset Management Ireland Limited",
            "IE0009FT4LX4",
            "IE",
            "EUR",
            null,
            null,
            new BigDecimal("50.1234"),
            new BigDecimal("5000000.00"),
            new BigDecimal("0.6500"),
            null);

    var equityRowWithCostBasis =
        new InvestmentReportRow(
            "Tuleva World Stock Index Fund",
            "Tuleva Fondid AS",
            "EE3600001707",
            "EE",
            "EUR",
            new BigDecimal("9.87"),
            new BigDecimal("1234567"),
            new BigDecimal("11.2233"),
            new BigDecimal("1500000"),
            new BigDecimal("0.1950"),
            new BigDecimal("0.0050"));

    var equitySection =
        new InvestmentReportContext.SecuritySection(
            "Aktsiafondid",
            List.of(equityRowWithoutCostBasis, equityRowWithCostBasis),
            new BigDecimal("1234567"),
            new BigDecimal("6500000"),
            new BigDecimal("0.8450"),
            new BigDecimal("0.0125"));

    var bondRowMissingCostAndPrice =
        new InvestmentReportRow(
            "Tuleva Maailma Võlakirjade Indeksifond",
            "Tuleva Fondid AS",
            "EE3600001715",
            "EE",
            "EUR",
            null,
            new BigDecimal("50000"),
            null,
            new BigDecimal("52000"),
            new BigDecimal("0.0068"),
            new BigDecimal("-0.0003"));

    var bondSection =
        new InvestmentReportContext.SecuritySection(
            "Võlakirjafondid",
            List.of(bondRowMissingCostAndPrice),
            null,
            new BigDecimal("52000"),
            new BigDecimal("0.0068"),
            new BigDecimal("-0.0010"));

    var domesticCashRow =
        new InvestmentReportRow(
            "Arvelduskonto",
            "AS SEB Pank",
            null,
            "EE",
            "EUR",
            null,
            null,
            null,
            new BigDecimal("300000"),
            new BigDecimal("0.0390"),
            null);

    var overdrawnForeignCashRow =
        new InvestmentReportRow(
            "Arveldus USD",
            "AS LHV Pank",
            null,
            "EE",
            "USD",
            null,
            null,
            null,
            new BigDecimal("-1500"),
            new BigDecimal("-0.0002"),
            null);

    return new InvestmentReportContext(
        "Tuleva Maailma Aktsiate Pensionifond",
        "30.06.2026",
        List.of(equitySection, bondSection),
        new BigDecimal("1284567"),
        new BigDecimal("6552000"),
        new BigDecimal("0.8518"),
        new BigDecimal("0.0115"),
        List.of(domesticCashRow, overdrawnForeignCashRow),
        new BigDecimal("298500"),
        new BigDecimal("0.0388"),
        new BigDecimal("-0.0007"),
        new BigDecimal("6850500"),
        new BigDecimal("1284567"),
        new BigDecimal("0.8906"),
        new BigDecimal("7690000"));
  }

  private InvestmentReportContext boundaryContext() {
    var unnamedPositionRow =
        new InvestmentReportRow(
            "Sularaha ootel positsioon",
            null,
            null,
            null,
            "EUR",
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null);

    var equitySection =
        new InvestmentReportContext.SecuritySection(
            "Aktsiafondid",
            List.of(unnamedPositionRow),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null);

    var zeroCashRow =
        new InvestmentReportRow(
            "Arvelduskonto",
            "AS SEB Pank",
            null,
            "EE",
            "EUR",
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null);

    var overdrawnCashRow =
        new InvestmentReportRow(
            "Arveldus USD",
            "AS LHV Pank",
            null,
            "EE",
            "USD",
            null,
            null,
            null,
            new BigDecimal("-250.50"),
            new BigDecimal("-0.0001"),
            null);

    return new InvestmentReportContext(
        "Tuleva III Samba Pensionifond",
        "31.01.2026",
        List.of(equitySection),
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        List.of(zeroCashRow, overdrawnCashRow),
        new BigDecimal("-250.50"),
        new BigDecimal("-0.0001"),
        null,
        new BigDecimal("-250.50"),
        null,
        new BigDecimal("-0.0001"),
        BigDecimal.ZERO);
  }

  private static ITemplateEngine createTemplateEngine() {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode("HTML");
    resolver.setCharacterEncoding("UTF-8");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
