// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.application.query.service;

import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.repository.AuthRepository;
import com.td.czghagent.domain.service.SessionToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthQueryService {

    private final AuthRepository authRepository;

    public AuthQueryService(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public Optional<CurrentUser> resolve(String rawToken) {
        String tokenHash = SessionToken.hash(rawToken);
        Optional<CurrentUser> user = authRepository.findBySessionTokenHash(tokenHash, LocalDateTime.now())
                .map(account -> account.toCurrentUser());
        user.ifPresent(ignored -> authRepository.touchSession(tokenHash, LocalDateTime.now()));
        return user;
    }
}
