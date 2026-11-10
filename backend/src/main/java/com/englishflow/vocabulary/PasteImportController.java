package com.englishflow.vocabulary;

import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vocabulary/bulk-import/paste")
public class PasteImportController {
    private final PasteImportService service;
    private final UserRepository users;
    public PasteImportController(PasteImportService service, UserRepository users) { this.service = service; this.users = users; }
    private UUID owner(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new BadCredentialsException("Unauthenticated");
        return users.findByEmail(auth.getName()).map(User::getId).orElseThrow(() -> new BadCredentialsException("Unauthenticated"));
    }
    @PostMapping("/preview") public PastePreviewResponse preview(@Valid @RequestBody PasteImportRequest request, Authentication auth) {
        return service.preview(owner(auth), request);
    }
    @PostMapping("/validate") public PastePreviewResponse validate(@Valid @RequestBody PasteBatchRequest request, Authentication auth) {
        return service.validate(owner(auth), request);
    }
    @PostMapping("/confirm") public PasteCommitResponse confirm(@Valid @RequestBody PasteBatchRequest request, Authentication auth) {
        return service.confirm(owner(auth), request);
    }
}
