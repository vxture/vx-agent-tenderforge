// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.security;

import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 把 RP 会话 cookie 解析成调用者身份，并在需要时静默续期。
 *
 * <p>续期挂在解析路径上而不是单独的定时任务：任务不知道哪些会话是活的，
 * 而每一次真实请求恰好证明了它的会话正在被使用。代价是续期发生在请求路径上，
 * 但那只在票快过期的那一次，且是一次内网调用。
 */
@Component
public class PlatformSessionResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformSessionResolver.class);

    private final RpSessionRepository sessions;
    private final OidcLoginService loginService;

    public PlatformSessionResolver(RpSessionRepository sessions, OidcLoginService loginService) {
        this.sessions = sessions;
        this.loginService = loginService;
    }

    /**
     * 解析会话。
     *
     * <p>返回空表示「这个 cookie 现在不能用」——不存在、已过期、或续期失败，
     * 三者对调用方是同一件事：请重新登录。区分它们只对攻击者有用。
     */
    public Optional<CurrentUser> resolve(String cookieValue) {
        LocalDateTime now = LocalDateTime.now();
        String tokenHash = SessionToken.hash(cookieValue);
        Optional<RpSession> found = sessions.findByTokenHash(tokenHash, now);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RpSession session = found.get();
        try {
            session = loginService.refreshIfNeeded(session);
        } catch (BusinessException exception) {
            // 刷新失败即会话死亡。删掉它，让下一次请求走干净的未登录路径，
            // 而不是每次都重试一个注定失败的刷新。
            LOGGER.info("RP session {} refresh failed, dropping it: {}",
                    session.id(), exception.getErrorCode());
            sessions.deleteByTokenHash(tokenHash);
            return Optional.empty();
        }
        sessions.touch(tokenHash, now);
        return Optional.of(session.toCurrentUser());
    }
}
