package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.IssuedSession;
import dev.kalbarczyk.striply.identity.model.dto.LoginUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;

public interface AuthService {
    RegisteredUser register(RegisterUserCommand command);
    IssuedSession login(LoginUserCommand command);
    IssuedSession refresh(String refreshToken);
    void logout(String refreshToken);
}
