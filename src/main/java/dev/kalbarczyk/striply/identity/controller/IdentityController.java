package dev.kalbarczyk.striply.identity.controller;

import dev.kalbarczyk.striply.identity.model.dto.IssuedSession;
import dev.kalbarczyk.striply.identity.model.dto.LoginUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;
import dev.kalbarczyk.striply.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IdentityController {

    private final AuthService authService;

    @PostMapping
    public RegisteredUser register(@RequestBody RegisterUserCommand command) {
        return authService.register(command);
    }

    @PostMapping("/login")
    public IssuedSession login(@RequestBody LoginUserCommand command) {
        return authService.login(command);
    }


}
