

package com.splitwisemoney.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // Forward the root request to the index.html static resource
        return "forward:/index.html";
    }
}
