package com.artverse.api;

import com.artverse.api.dto.ChapterDto;
import com.artverse.application.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @PostMapping("/generate-novel")
    public Map<String, String> generateNovel(@PathVariable Long chapterId) {
        String content = novelService.generateNovel(chapterId);
        return Map.of("novel_content", content);
    }

    @PostMapping("/import-novel")
    public ChapterDto importNovel(@PathVariable Long chapterId, @RequestBody Map<String, String> body) {
        return ChapterDto.from(novelService.importNovel(chapterId, body.get("content")));
    }
}
