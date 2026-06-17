package com.artverse.api.dto;

import com.artverse.domain.MangaAgentMessage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MangaAgentDtos {
    private MangaAgentDtos() {
    }

    public record RunRequest(String message, UUID requestId) {
    }

    public record RunResponse(String reply, UUID requestId) {
    }

    public record MessageDto(Long id, String role, String content, UUID requestId, OffsetDateTime createdAt) {
        public static MessageDto from(MangaAgentMessage message) {
            return new MessageDto(
                    message.getId(),
                    message.getRole().name().toLowerCase(),
                    message.getContent(),
                    message.getRequestId(),
                    message.getCreatedAt()
            );
        }
    }

    public record MessagesResponse(List<MessageDto> messages) {
    }
}
