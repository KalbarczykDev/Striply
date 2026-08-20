package dev.kalbarczyk.striply.configuration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityTestController {

    @GetMapping("/test")
    String test() {
        return "test";
    }
}
