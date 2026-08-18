package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;

public interface AuthService {
    RegisteredUser register(RegisterUserCommand command);
}
