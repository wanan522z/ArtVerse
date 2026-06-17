package com.artverse.api;

import com.artverse.application.CurrentUserService;
import com.artverse.application.MangaAgentService;
import com.artverse.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}/manga-agent")
@RequiredArgsConstructor
public class MangaAgentController {

    private final MangaAgentService mangaAgentService;
    private final CurrentUserService currentUserService;

    @PostMapping("/run")
    public Map<String, Object> run(@PathVariable Long chapterId, @RequestBody Map<String, String> body) {
        User user = currentUserService.requireCurrentUser();
        String reply = mangaAgentService.run(chapterId, body.get("message"), user);
        return Map.of("reply", reply);
    }
}
