package ee.tuleva.onboarding.mandate;

import com.lowagie.text.DocumentException;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PdfService {

    public void print() throws DocumentException, IOException {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("XHTML");
        templateResolver.setCharacterEncoding("UTF-8");

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        Context ctx = new Context();
        ctx.setVariable("name", "Lalalal");
        String htmlContent = templateEngine.process("mandate", ctx);

        ByteOutputStream os = new ByteOutputStream();
        ITextRenderer renderer = new ITextRenderer();

        renderer.setDocumentFromString(htmlContent);
        renderer.layout();
        renderer.createPDF(os);

        byte[] pdfAsBytes = os.getBytes();
        os.close();

        FileOutputStream fos = new FileOutputStream(new File("/Users/jordan.valdma/Downloads/mandate.pdf"));
        fos.write(pdfAsBytes);
        fos.close();

    }

}
