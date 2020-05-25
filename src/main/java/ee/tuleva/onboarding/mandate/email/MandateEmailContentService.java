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
    private final HttpServletRequest request; // TODO: remove dependency on HttpServletRequest

    public String getSecondPillarHtml() {
        Context context = new Context();
        context.setLocale(localeResolver.resolveLocale(request));
        return templateEngine.process("second_pillar_mandate", context);
    }

    public String getThirdPillarHtml(String pensionAccountNumber) {
        Context context = new Context();
        context.setLocale(localeResolver.resolveLocale(request));
        context.setVariable("pensionAccountNumber", pensionAccountNumber);
        return templateEngine.process("third_pillar_mandate", context);
    }
}