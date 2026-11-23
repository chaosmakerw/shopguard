package com.codesprint.lesson0831.trade.api;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile({"prod", "trade-demo"})
public class TradePageController {
    @GetMapping({"/trade", "/trade/"})
    public String index() {
        return "forward:/trade/index.html";
    }
}
