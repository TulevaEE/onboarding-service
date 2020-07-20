package ee.tuleva.onboarding.mandate.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class MandateEmailContentService {

    private final TemplateEngine templateEngine;

    public String getSecondPillarHtml(boolean isFullyConverted, Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale);
        ctx.setVariable("isThirdPillarFullyConverted", isFullyConverted);
        return templateEngine.process("second_pillar_mandate", ctx);
    }

    public String getThirdPillarHtml(String pensionAccountNumber, boolean isFullyConverted, Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale);
        ctx.setVariable("pensionAccountNumber", pensionAccountNumber);
        ctx.setVariable("isSecondPillarFullyConverted", isFullyConverted);
        return templateEngine.process("third_pillar_mandate", ctx);
    }
}