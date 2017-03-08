package ee.tuleva;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/aMBCK-fCYjDSDVrughzDsemdZaAZLNsQ1LhATtLExa8")
	@ResponseBody
	public String challenge() {
		return "aMBCK-fCYjDSDVrughzDsemdZaAZLNsQ1LhATtLExa8.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
