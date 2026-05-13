package ee.tuleva.onboarding.investment.report.publishing.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class InvestmentReportPdfGeneratorTest {

  private final ITemplateEngine templateEngine = createTemplateEngine();
  private final InvestmentReportPdfGenerator generator =
      new InvestmentReportPdfGenerator(templateEngine);

  @Test
  void generatePdfProducesValidPdfBytes() {
    var context = sampleContext();

    var pdfBytes = generator.generatePdf(context);

    assertThat(pdfBytes).isNotEmpty();
    assertThat(pdfBytes).startsWith(new byte[] {'%', 'P', 'D', 'F'});
  }

  @Test
  void generateHtmlContainsFundTitleAndDate() {
    var context = sampleContext();

    var html = generator.generateHtml(context);

    assertThat(html).contains("Tuleva Maailma Aktsiate Pensionifond");
    assertThat(html).contains("31.03.2026");
    assertThat(html).contains("CCF Developed World");
    assertThat(html).contains("FONDIOSAKUD KOKKU");
    assertThat(html).contains("HOIUSED KOKKU");
    assertThat(html).contains("FONDI PUHASVÄÄRTUS");
  }

  @Test
  void generateHtmlRendersPlaceholderForNullAvgCost() {
    var context = sampleContext();

    var html = generator.generateHtml(context);

    assertThat(html).contains("&#8212;");
  }

  private InvestmentReportContext sampleContext() {
    var secRow =
        new InvestmentReportRow(
            "CCF Developed World",
            "BlackRock",
            "IE0009FT4LX4",
            "IE",
            "EUR",
            null,
            null,
            new BigDecimal("50.12"),
            new BigDecimal("5000000"),
            new BigDecimal("0.9500"),
            null);

    var section =
        new InvestmentReportContext.SecuritySection(
            "Aktsiafondid",
            List.of(secRow),
            null,
            new BigDecimal("5000000"),
            new BigDecimal("0.9500"),
            new BigDecimal("0.0100"));

    var cashRow =
        new InvestmentReportRow(
            "Arvelduskonto",
            "AS SEB Pank",
            null,
            "EE",
            "EUR",
            null,
            null,
            null,
            new BigDecimal("250000"),
            new BigDecimal("0.0476"),
            null);

    return new InvestmentReportContext(
        "Tuleva Maailma Aktsiate Pensionifond",
        "31.03.2026",
        List.of(section),
        null,
        new BigDecimal("5000000"),
        new BigDecimal("0.9500"),
        new BigDecimal("0.0100"),
        List.of(cashRow),
        new BigDecimal("250000"),
        new BigDecimal("0.0476"),
        new BigDecimal("-0.0020"),
        new BigDecimal("5250000"),
        null,
        new BigDecimal("0.9976"),
        new BigDecimal("5263000"));
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
