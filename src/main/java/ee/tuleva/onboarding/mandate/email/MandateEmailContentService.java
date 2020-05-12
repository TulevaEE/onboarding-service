package ee.tuleva.onboarding.mandate.email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class MandateEmailContentService {

    private final TemplateEngine templateEngine;

    public String getSecondPillarHtml(Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale); //localeResolver.resolveLocale(request));
        String htmlContent = templateEngine.process("second_pillar_mandate", ctx);
        return htmlContent;
    }

    public String getThirdPillarHtml(String pensionAccountNumber, Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale); // localeResolver.resolveLocale(request));
        ctx.setVariable("pensionAccountNumber", pensionAccountNumber);
        String htmlContent = templateEngine.process("third_pillar_mandate", ctx);
        return htmlContent;
    }
}