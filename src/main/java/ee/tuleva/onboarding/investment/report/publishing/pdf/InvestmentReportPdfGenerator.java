package ee.tuleva.onboarding.investment.report.publishing.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentReportPdfGenerator {

  private final ITemplateEngine templateEngine;

  public byte[] generatePdf(InvestmentReportContext reportContext) {
    var context = buildThymeleafContext(reportContext);
    var html = templateEngine.process("investment_report", context);

    try (var os = new ByteArrayOutputStream()) {
      var builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(html, null);
      builder.toStream(os);
      builder.run();
      log.info("Generated PDF for fund={}, size={}bytes", reportContext.fundTitle(), os.size());
      return os.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to generate PDF for fund=%s".formatted(reportContext.fundTitle()), e);
    }
  }

  String generateHtml(InvestmentReportContext reportContext) {
    return templateEngine.process("investment_report", buildThymeleafContext(reportContext));
  }

  private Context buildThymeleafContext(InvestmentReportContext rc) {
    var ctx = new Context();
    ctx.setVariable("fundTitle", rc.fundTitle());
    ctx.setVariable("reportDate", rc.reportDate());
    ctx.setVariable("securitiesSections", rc.securitiesSections());
    ctx.setVariable("securitiesTotalCost", rc.securitiesTotalCost());
    ctx.setVariable("securitiesTotalMarketValue", rc.securitiesTotalMarketValue());
    ctx.setVariable("securitiesTotalNavPercent", rc.securitiesTotalNavPercent());
    ctx.setVariable("securitiesTotalChange", rc.securitiesTotalChange());
    ctx.setVariable("cashRows", rc.cashRows());
    ctx.setVariable("cashTotalMarketValue", rc.cashTotalMarketValue());
    ctx.setVariable("cashTotalNavPercent", rc.cashTotalNavPercent());
    ctx.setVariable("cashTotalChange", rc.cashTotalChange());
    ctx.setVariable("totalAssetsMarketValue", rc.totalAssetsMarketValue());
    ctx.setVariable("totalAssetsCost", rc.totalAssetsCost());
    ctx.setVariable("totalAssetsNavPercent", rc.totalAssetsNavPercent());
    ctx.setVariable("fundNav", rc.fundNav());
    return ctx;
  }
}
