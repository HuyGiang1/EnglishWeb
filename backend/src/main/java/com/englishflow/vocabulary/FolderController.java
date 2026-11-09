package com.englishflow.vocabulary;

import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {
    private final FolderService service;
    private final UserRepository users;
    public FolderController(FolderService service, UserRepository users) { this.service = service; this.users = users; }
    private UUID owner(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new BadCredentialsException("Unauthenticated");
        return users.findByEmail(auth.getName()).map(User::getId)
            .orElseThrow(() -> new BadCredentialsException("Unauthenticated"));
    }
    @GetMapping public FolderPageResponse list(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size, Authentication auth) { return service.list(owner(auth), page, size); }
    @GetMapping("/{id}") public FolderResponse get(@PathVariable UUID id, Authentication auth) { return service.get(owner(auth), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse create(@Valid @RequestBody FolderRequest request, Authentication auth) { return service.create(owner(auth), request); }
    @PutMapping("/{id}") public FolderResponse update(@PathVariable UUID id,
        @Valid @RequestBody FolderRequest request, Authentication auth) { return service.update(owner(auth), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) { service.delete(owner(auth), id); }
}
