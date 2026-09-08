// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.AccountCommandService;
import com.td.czghagent.application.query.service.AccountQueryService;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.rest.dto.ChangePasswordRequest;
import com.td.czghagent.rest.dto.UpdateProfileRequest;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountCommandService commandService;
    private final AccountQueryService queryService;

    public AccountController(AccountCommandService commandService, AccountQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CurrentUser updateAvatar(@RequestParam MultipartFile file,
                                                 HttpServletRequest request) throws IOException {
        return commandService.updateAvatar(file.getBytes(), RequestIdentity.operation(request));
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar(HttpServletRequest request) {
        StoredFile file = queryService.readAvatar(RequestIdentity.user(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(file.size());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePrivate());
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    @PatchMapping("/password")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                            HttpServletRequest request) {
        commandService.changePassword(
                body.currentPassword(), body.newPassword(), RequestIdentity.operation(request)
        );
    }

    @PatchMapping("/profile")
    public CurrentUser updateProfile(@Valid @RequestBody UpdateProfileRequest body,
                                                  HttpServletRequest request) {
        return commandService.updateProfile(body.displayName(), RequestIdentity.operation(request));
    }
}

