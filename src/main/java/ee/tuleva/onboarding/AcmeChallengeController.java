package ee.tuleva.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/ImI3Zo4v6MLlQcnQDhTYn1ojxtwALQzOaoGTDZFANhk")
	@ResponseBody
	public String challenge() {
		return "ImI3Zo4v6MLlQcnQDhTYn1ojxtwALQzOaoGTDZFANhk.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
