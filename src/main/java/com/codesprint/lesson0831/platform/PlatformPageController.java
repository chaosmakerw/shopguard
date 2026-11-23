package com.codesprint.lesson0831.platform;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("platform")
public class PlatformPageController {
    @GetMapping("/platform/") public String platform(){return "forward:/platform/index.html";}
    @GetMapping({"/","/platform"}) public String index(){return "redirect:/platform/";}
}
