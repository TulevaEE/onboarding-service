package ee.tuleva.onboarding.mandate.pdf;

import com.lowagie.text.DocumentException;
import ee.tuleva.onboarding.mandate.Mandate;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfService {

    public byte[] toPdf(Mandate mandate) {
        try {
            return generatePdf(mandate);
        } catch (DocumentException | IOException e) {
            throw new PdfGenerationException(e);
        }
    }

    public byte[] generatePdf(Mandate mandate) throws DocumentException, IOException {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/pdf/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("XHTML");
        templateResolver.setCharacterEncoding("UTF-8");

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        Context ctx = new Context();
        ctx.setVariable("name", "Lalalal");
        String htmlContent = templateEngine.process("mandate", ctx);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();

        renderer.setDocumentFromString(htmlContent);
        renderer.layout();
        renderer.createPDF(os);

        byte[] pdfAsBytes = os.toByteArray();
        os.close();

        return pdfAsBytes;
//        FileOutputStream fos = new FileOutputStream(new File("/Users/jordan.valdma/Downloads/mandate.pdf"));
//        fos.write(pdfAsBytes);
//        fos.close();

    }

}
