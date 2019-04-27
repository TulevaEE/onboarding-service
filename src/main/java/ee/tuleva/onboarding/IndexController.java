package ee.tuleva.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class IndexController {

    @RequestMapping("/")
    public String index() {
        return "redirect:/swagger-ui.html";
    }

    // Temporary fix for health endpoint change
    @RequestMapping("/health")
    public String health() {
        return "forward:/actuator/health";
    }

}
