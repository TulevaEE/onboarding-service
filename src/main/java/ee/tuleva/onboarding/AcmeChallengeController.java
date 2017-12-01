package ee.tuleva.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/hiPzrb01y82bTVL1T2XfyFuXDVH0d7Qi7VcD25CE8zo")
	@ResponseBody
	public String challenge() {
		return "hiPzrb01y82bTVL1T2XfyFuXDVH0d7Qi7VcD25CE8zo.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
