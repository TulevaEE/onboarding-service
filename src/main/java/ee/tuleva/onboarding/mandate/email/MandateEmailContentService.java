package ee.tuleva.onboarding.mandate.email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class MandateEmailContentService {

    private final TemplateEngine templateEngine;
    private final LocaleResolver localeResolver;
    private final HttpServletRequest request;

    public String getSecondPillarHtml() {
        Context ctx = new Context();
        ctx.setLocale(localeResolver.resolveLocale(request));
        String htmlContent = templateEngine.process("second_pillar_mandate", ctx);
        return htmlContent;
    }

    public String getThirdPillarHtml(String pensionAccountNumber) {
        Context ctx = new Context();
        ctx.setLocale(localeResolver.resolveLocale(request));
        ctx.setVariable("pensionAccountNumber", pensionAccountNumber);
        String htmlContent = templateEngine.process("third_pillar_mandate", ctx);
        return htmlContent;
    }
}