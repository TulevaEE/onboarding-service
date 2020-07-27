package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.user.User;
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

    public String getSecondPillarHtml(User user, boolean isFullyConverted, boolean isThirdPillarActive, Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale);
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("isMember", user.isMember());
        ctx.setVariable("isThirdPillarActive", isThirdPillarActive);
        ctx.setVariable("isThirdPillarFullyConverted", isFullyConverted);
        return templateEngine.process("second_pillar_mandate", ctx);
    }

    public String getThirdPillarHtml(User user, String pensionAccountNumber, boolean isFullyConverted,
                                     boolean isSecondPillarActive, Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale);
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("isMember", user.isMember());
        ctx.setVariable("isSecondPillarActive", isSecondPillarActive);
        ctx.setVariable("isSecondPillarFullyConverted", isFullyConverted);
        ctx.setVariable("pensionAccountNumber", pensionAccountNumber);
        return templateEngine.process("third_pillar_mandate", ctx);
    }
}