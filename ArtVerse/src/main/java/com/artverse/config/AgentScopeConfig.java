package com.artverse.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class AgentScopeConfig {

    private static final Path DEFAULT_WORKSPACE = Paths.get(System.getProperty("user.dir", "."), ".agentscope/workspace");

    @Bean
    public Dotenv dotenv() {
        Path envFile = Paths.get(".env").toAbsolutePath();
        if (Files.exists(envFile)) {
            log.info("Loading .env from: {}", envFile);
            return Dotenv.configure().directory(envFile.getParent().toString()).load();
        }
        log.debug(".env file not found at: {}", envFile);
        return Dotenv.configure().ignoreIfMissing().load();
    }

    private static String readFromEnvFile(String key) {
        Path envFile = Paths.get(".env").toAbsolutePath();
        if (!Files.exists(envFile)) return null;
        try {
            return Files.lines(envFile)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> line.startsWith(key + "=") || line.startsWith(key + " ="))
                    .map(line -> {
                        int eq = line.indexOf('=');
                        String value = line.substring(eq + 1).trim();
                        if (value.length() >= 2) {
                            char first = value.charAt(0);
                            char last = value.charAt(value.length() - 1);
                            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                                value = value.substring(1, value.length() - 1);
                            }
                        }
                        return value;
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to read .env file: {}", e.getMessage());
            return null;
        }
    }

    private static String maskKey(String key) {
        if (key == null || key.length() <= 8) return "(not set)";
        return key.substring(0, 7) + "****" + key.substring(key.length() - 4);
    }

    @Bean
    public Path agentScopeWorkspace() {
        try {
            Files.createDirectories(DEFAULT_WORKSPACE);
            Path agentsMd = DEFAULT_WORKSPACE.resolve("AGENTS.md");
            if (!Files.exists(agentsMd)) {
                Files.writeString(agentsMd, """
                        # ArtVerse AI Creative Assistant

                        You help users create novels and manga content in ArtVerse.

                        ## Behavior
                        - Answer in concise Chinese.
                        - Keep character settings consistent.
                        - Explain uncertainty clearly.
                        - Use available tools for workflow actions instead of pretending work is done.
                        """);
            }
        } catch (IOException e) {
            log.warn("Failed to init AgentScope workspace: {}", e.getMessage());
        }
        return DEFAULT_WORKSPACE;
    }

    @Bean
    public CompactionConfig defaultCompactionConfig() {
        return CompactionConfig.builder()
                .triggerMessages(30)
                .keepMessages(10)
                .flushBeforeCompact(true)
                .build();
    }

    @Bean
    public Model deepSeekModel(ArtVerseProperties properties, Dotenv dotenv) {
        String apiKey = properties.getDeepseek().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = readFromEnvFile("DEEPSEEK_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = dotenv.get("DEEPSEEK_API_KEY");
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("DeepSeek API key not configured at system level; per-user keys will be used.");
        }
        log.info("DeepSeek model configured with key: {}", maskKey(apiKey));
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.getDeepseek().getModel())
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .stream(true)
                .build();
    }
}
