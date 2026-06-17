package com.artverse.application;

import com.artverse.agents.AgentMessage;
import com.artverse.agents.AgentRunRequest;
import com.artverse.agents.AgentTaskType;
import com.artverse.agents.HarnessAgentGateway;
import com.artverse.common.BusinessException;
import com.artverse.domain.User;
import com.artverse.persistence.ChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MangaAgentService {

    private final ChapterRepository chapterRepository;
    private final HarnessAgentGateway harnessAgentGateway;
    private final ApiKeyService apiKeyService;

    @Transactional(readOnly = true)
    public String run(Long chapterId, String message, User user) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(400, "Message cannot be empty");
        }
        var chapter = chapterRepository.findByIdForIdempotency(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(new AgentMessage("system", buildSystemPrompt(chapterId, user)));
        messages.add(new AgentMessage("user", message));

        String deepseekApiKey = apiKeyService.getDecryptedKey(user, "deepseek");
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            throw new BusinessException(400, "请先在设置中配置 DeepSeek API Key 后再使用漫画智能体");
        }

        AgentRunRequest request = new AgentRunRequest(
                String.valueOf(user.getId()),
                chapter.getStory().getId(),
                chapterId,
                AgentTaskType.MANGA_DIRECTOR,
                messages,
                Map.of(
                        "chapter_id", chapterId,
                        "coze_api_key", nullToBlank(apiKeyService.getDecryptedKey(user, "coze"))
                ),
                deepseekApiKey
        );

        try {
            String result = harnessAgentGateway.generateText(request).block();
            if (result == null || result.isBlank()) {
                throw new BusinessException(502, "Agent returned empty response");
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "Agent service failed: " + (e.getMessage() == null ? "unknown error" : e.getMessage()));
        }
    }

    private String buildSystemPrompt(Long chapterId, User user) {
        return """
                You are ArtVerse Manga Director, an AI workflow assistant for Chinese AI manga creation.
                Always answer in concise Chinese.

                Current user id: %s
                Current chapter id: %s

                You can use tools to inspect chapter context, generate storyboard scenes, and save edited storyboard scenes.
                Rules:
                - First inspect chapter context when the user asks about the manga workflow.
                - If source content is missing, tell the user to write chat content or import novel text first.
                - If storyboard scenes are missing and the user asks to continue, generate storyboard scenes.
                - Do not directly claim that images have been generated. Image generation is a long-running SSE task handled by the existing Generate Manga action.
                - After storyboard is ready, clearly tell the user that they can click Generate Manga, or ask you to refine scenes.
                - Keep business actions explicit and summarize what changed.
                """.formatted(user.getId(), chapterId);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
