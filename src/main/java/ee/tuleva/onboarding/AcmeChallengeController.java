package ee.tuleva.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/10BYosEqVYQOSL7nmbpS0zsFRitpmEPwb1KbPvBIWuI")
	@ResponseBody
	public String challenge() {
		return "10BYosEqVYQOSL7nmbpS0zsFRitpmEPwb1KbPvBIWuI.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
