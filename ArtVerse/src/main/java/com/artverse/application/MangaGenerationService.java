package com.artverse.application;

import com.artverse.ai.GeneratedImage;
import com.artverse.ai.Image2Client;
import com.artverse.ai.ImageGenerationRequest;
import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.artverse.domain.*;
import com.artverse.media.MediaStorageService;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.MangaImageRepository;
import com.artverse.storage.ObjectStorageService;
import com.artverse.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MangaGenerationService {

    private final ChapterRepository chapterRepository;
    private final MangaImageRepository mangaImageRepository;
    private final Image2Client image2Client;
    private final ObjectStorageService objectStorageService;
    private final MediaStorageService mediaStorageService;
    private final CharacterProfileService characterProfileService;
    private final ArtVerseProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<Long, MangaGenerationJob> activeJobs = new ConcurrentHashMap<>();
    private ExecutorService executor;

    @PostConstruct
    void init() {
        executor = Executors.newCachedThreadPool();
    }

    public SseEmitter generateMangaStream(Long chapterId, String imageApiKey) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        // Check if already running
        MangaGenerationJob existingJob = activeJobs.get(chapterId);
        if (existingJob != null && existingJob.isRunning()) {
            SseEmitter emitter = new SseEmitter(0L);
            existingJob.addSubscriber(emitter);
            return emitter;
        }

        List<String> scenes = resolveScenesForImageGeneration(chapter);
        if (scenes.size() != chapter.getImageCount()) {
            throw new BusinessException(400, "Scenes count (" + scenes.size() + ") does not match image count (" + chapter.getImageCount() + ")");
        }

        MangaGenerationJob job = new MangaGenerationJob(chapterId, scenes);
        activeJobs.put(chapterId, job);

        SseEmitter emitter = new SseEmitter(0L);
        job.addSubscriber(emitter);

        // Start generation in background
        executor.submit(() -> runGenerationJob(job, chapter, imageApiKey));

        return emitter;
    }

    private void runGenerationJob(MangaGenerationJob job, Chapter chapter, String imageApiKey) {
        try {
            // Send scenes event
            job.broadcastEvent("event: scenes\ndata: " + objectMapper.writeValueAsString(Map.of("scenes", job.getScenes())) + "\n\n");

            Map<String, Object> profileResult = characterProfileService.resolveEffective(chapter.getId());
            String profiles = (String) profileResult.get("content");

            List<Path> refImages = computeEffectiveRefImages(chapter);
            List<Path> tempRefImages = new ArrayList<>();
            List<Path> imageRequestRefs = materializeMinioRefs(refImages, tempRefImages);

            try {
                for (int i = 0; i < job.getScenes().size(); i++) {
                    if (!job.isRunning()) break;

                    int imageNumber = i + 1;
                    String scene = job.getScenes().get(i);

                    // Check if image already exists
                    Optional<MangaImage> existing = mangaImageRepository.findByChapterIdAndImageNumber(chapter.getId(), imageNumber);
                    if (existing.isPresent()) {
                        MangaImage img = existing.get();
                        String url = "/static/manga/" + img.getImagePath();
                        job.broadcastEvent("event: image\ndata: " + objectMapper.writeValueAsString(Map.of(
                                "image_number", imageNumber,
                                "image_path", img.getImagePath(),
                                "url", url
                        )) + "\n\n");
                        continue;
                    }

                    // Send progress
                    job.broadcastEvent("event: progress\ndata: " + objectMapper.writeValueAsString(Map.of(
                            "image_number", imageNumber,
                            "total", job.getScenes().size(),
                            "message", "Generating page " + imageNumber + "/" + job.getScenes().size()
                    )) + "\n\n");

                    // Build prompt
                    String prompt = buildImagePrompt(scene, profiles, chapter.getColorMode());

                    // Generate image
                    ImageGenerationRequest request = new ImageGenerationRequest(
                            prompt,
                            properties.getImage().getModel(),
                            properties.getImage().getSize(),
                            imageRequestRefs,
                            chapter.getColorMode().name().toLowerCase()
                    );

                    GeneratedImage generated = image2Client.generate(request, imageApiKey).block();
                    if (generated == null) {
                        throw new BusinessException(502, "Image generation returned null for page " + imageNumber);
                    }

                    // Upload to MinIO
                    String filename = mediaStorageService.generateUniqueFilename("panel_" + String.format("%02d", imageNumber), ".png");
                    String objectKey = "stories/" + chapter.getStory().getId() + "/chapters/" + chapter.getId() + "/panels/" + filename;
                    StoredObject stored = objectStorageService.putPng(objectKey, generated.localFile(), "image/png");

                    // Save to DB
                    MangaImage mangaImage = new MangaImage();
                    mangaImage.setChapter(chapter);
                    mangaImage.setImageNumber(imageNumber);
                    mangaImage.setImagePath(stored.objectKey());
                    mangaImage.setStorageProvider(StorageProvider.MINIO);
                    mangaImage.setBucket(stored.bucket());
                    mangaImage.setObjectKey(stored.objectKey());
                    mangaImage.setContentType(stored.contentType());
                    mangaImage.setSizeBytes(stored.sizeBytes());
                    mangaImage.setPrompt(prompt);
                    mangaImageRepository.save(mangaImage);

                    // Send image event
                    String url = "/static/manga/" + mangaImage.getImagePath();
                    job.broadcastEvent("event: image\ndata: " + objectMapper.writeValueAsString(Map.of(
                            "image_number", imageNumber,
                            "image_path", mangaImage.getImagePath(),
                            "url", url
                    )) + "\n\n");

                    // Cleanup temp file
                    try {
                        Files.deleteIfExists(generated.localFile());
                        Files.deleteIfExists(generated.localFile().getParent());
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                cleanupTempFiles(tempRefImages);
            }

            // Send done
            job.broadcastEvent("event: done\ndata: " + objectMapper.writeValueAsString(Map.of("images", job.getScenes().size())) + "\n\n");
            job.complete();

        } catch (Exception e) {
            log.error("Manga generation failed for chapter {}: {}", chapter.getId(), e.getMessage(), e);
            try {
                job.broadcastEvent("event: error\ndata: " + objectMapper.writeValueAsString(Map.of("detail", e.getMessage())) + "\n\n");
            } catch (Exception ignored) {
            }
            job.error(e.getMessage());
        } finally {
            activeJobs.remove(chapter.getId());
        }
    }

    @Transactional
    public MangaImage regenerateImage(Long chapterId, int imageNumber, String prompt, String imageApiKey) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        if (imageNumber < 1 || imageNumber > chapter.getImageCount()) {
            throw new BusinessException(400, "Image number must be between 1 and " + chapter.getImageCount());
        }
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(400, "Prompt cannot be empty");
        }

        // Update scene if full scenes exist
        List<String> scenes = parseScenes(chapter.getScenesText());
        if (scenes.size() == chapter.getImageCount()) {
            scenes.set(imageNumber - 1, prompt);
            chapter.setScenesText(objectMapper.valueToTree(scenes).toString());
            chapterRepository.save(chapter);
        }

        Map<String, Object> profileResult = characterProfileService.resolveEffective(chapterId);
        String profiles = (String) profileResult.get("content");
        List<Path> refImages = computeEffectiveRefImages(chapter);
        List<Path> tempRefImages = new ArrayList<>();
        List<Path> imageRequestRefs = materializeMinioRefs(refImages, tempRefImages);

        String fullPrompt = buildImagePrompt(prompt, profiles, chapter.getColorMode());

        ImageGenerationRequest request = new ImageGenerationRequest(
                fullPrompt,
                properties.getImage().getModel(),
                properties.getImage().getSize(),
                imageRequestRefs,
                chapter.getColorMode().name().toLowerCase()
        );

        GeneratedImage generated;
        try {
            generated = image2Client.generate(request, imageApiKey).block();
        } finally {
            cleanupTempFiles(tempRefImages);
        }
        if (generated == null) {
            throw new BusinessException(502, "Image generation returned null");
        }

        // Upload to MinIO
        String filename = mediaStorageService.generateUniqueFilename("panel_" + String.format("%02d", imageNumber), ".png");
        String objectKey = "stories/" + chapter.getStory().getId() + "/chapters/" + chapterId + "/panels/" + filename;
        StoredObject stored = objectStorageService.putPng(objectKey, generated.localFile(), "image/png");

        // Update or create DB record
        Optional<MangaImage> existingOpt = mangaImageRepository.findByChapterIdAndImageNumber(chapterId, imageNumber);

        String oldObjectKey = null;
        String oldBucket = null;

        if (existingOpt.isPresent()) {
            MangaImage existing = existingOpt.get();
            oldObjectKey = existing.getObjectKey();
            oldBucket = existing.getBucket();
            existing.setImagePath(stored.objectKey());
            existing.setStorageProvider(StorageProvider.MINIO);
            existing.setBucket(stored.bucket());
            existing.setObjectKey(stored.objectKey());
            existing.setContentType(stored.contentType());
            existing.setSizeBytes(stored.sizeBytes());
            existing.setPrompt(fullPrompt);
            MangaImage saved = mangaImageRepository.save(existing);

            // Delete old object after successful save
            cleanupOldObject(oldBucket, oldObjectKey);

            // Cleanup temp
            cleanupTempFile(generated.localFile());

            return saved;
        } else {
            MangaImage mangaImage = new MangaImage();
            mangaImage.setChapter(chapter);
            mangaImage.setImageNumber(imageNumber);
            mangaImage.setImagePath(stored.objectKey());
            mangaImage.setStorageProvider(StorageProvider.MINIO);
            mangaImage.setBucket(stored.bucket());
            mangaImage.setObjectKey(stored.objectKey());
            mangaImage.setContentType(stored.contentType());
            mangaImage.setSizeBytes(stored.sizeBytes());
            mangaImage.setPrompt(fullPrompt);
            MangaImage saved = mangaImageRepository.save(mangaImage);

            cleanupTempFile(generated.localFile());

            return saved;
        }
    }

    private void cleanupOldObject(String bucket, String objectKey) {
        if (bucket != null && objectKey != null) {
            try {
                objectStorageService.deleteBestEffort(bucket, objectKey);
            } catch (Exception e) {
                log.warn("Failed to delete old MinIO object {}/{}: {}", bucket, objectKey, e.getMessage());
            }
        }
    }

    private void cleanupTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempFile.getParent());
        } catch (Exception ignored) {
        }
    }

    private List<Path> computeEffectiveRefImages(Chapter chapter) {
        List<Path> refs = new ArrayList<>();

        addMinioRefs(refs, "stories/" + chapter.getStory().getId() + "/chapters/" + chapter.getId() + "/ref_images/");

        // Chapter old single ref
        if (refs.isEmpty() && chapter.getRefImage() != null && !chapter.getRefImage().isBlank()) {
            Path ref = mediaStorageService.resolveRelativePath(chapter.getRefImage());
            if (ref != null && Files.exists(ref)) refs.add(ref);
        }

        if (refs.isEmpty() && chapter.getAssetGroup() != null) {
            addMinioRefs(refs, "stories/" + chapter.getStory().getId() + "/asset_groups/" + chapter.getAssetGroup().getId() + "/ref_images/");
        }

        if (refs.isEmpty()) {
            addMinioRefs(refs, "stories/" + chapter.getStory().getId() + "/ref_images/");
        }

        // Story old single ref
        if (refs.isEmpty() && chapter.getStory().getRefImage() != null && !chapter.getStory().getRefImage().isBlank()) {
            Path ref = mediaStorageService.resolveRelativePath(chapter.getStory().getRefImage());
            if (ref != null && Files.exists(ref)) refs.add(ref);
        }

        return refs;
    }

    private void addMinioRefs(List<Path> refs, String prefix) {
        objectStorageService.list(properties.getMinio().getBucket(), prefix, 4).stream()
                .map(stored -> Path.of(stored.objectKey()))
                .filter(this::isImageFile)
                .limit(4 - refs.size())
                .forEach(refs::add);
    }

    private List<Path> materializeMinioRefs(List<Path> refs, List<Path> tempRefs) {
        List<Path> materialized = new ArrayList<>();
        for (Path ref : refs) {
            if (Files.exists(ref)) {
                materialized.add(ref);
            } else {
                Path temp = downloadRefObject(ref.toString().replace('\\', '/'));
                tempRefs.add(temp);
                materialized.add(temp);
            }
        }
        return materialized;
    }

    private Path downloadRefObject(String objectKey) {
        try (var in = objectStorageService.get(properties.getMinio().getBucket(), objectKey)) {
            Path temp = Files.createTempFile("artverse-ref-", suffixFor(objectKey));
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download ref image: " + e.getMessage(), e);
        }
    }

    private String suffixFor(String objectKey) {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".webp")) return ".webp";
        return ".png";
    }

    private void cleanupTempFiles(List<Path> tempFiles) {
        for (Path tempFile : tempFiles) {
            cleanupTempFile(tempFile);
        }
    }

    private boolean isImageFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private String buildImagePrompt(String scene, String profiles, ColorMode colorMode) {
        StringBuilder sb = new StringBuilder();

        if (colorMode == ColorMode.BW) {
            sb.append("Japanese manga style black and white page, vertical multi-panel layout, clear panel borders, ");
            sb.append("Chinese speech bubbles, sound effects, high contrast lighting, fine lines and screentone. ");
        } else {
            sb.append("Japanese manga style color illustration page, cel-shading, vibrant colors, soft lighting, ");
            sb.append("Chinese speech bubbles, sound effects. ");
        }

        if (profiles != null && !profiles.isBlank()) {
            sb.append("Character profiles: ").append(profiles).append(". ");
        }

        sb.append("Scene: ").append(scene);

        return sb.toString();
    }

    private List<String> resolveScenesForImageGeneration(Chapter chapter) {
        List<String> scenes = parseScenes(chapter.getScenesText());
        if (!scenes.isEmpty()) {
            return scenes;
        }

        String material = chapter.novelContentOrJoinedMessages();
        if (material.isBlank()) {
            throw new BusinessException(400, "No source content available for manga generation.");
        }

        int imageCount = chapter.getImageCount();
        List<String> fallbackScenes = new ArrayList<>(imageCount);
        for (int i = 0; i < imageCount; i++) {
            fallbackScenes.add("Page " + (i + 1) + " of " + imageCount + ". Adapt this source content into a complete manga page: " + material);
        }
        return fallbackScenes;
    }

    private List<String> parseScenes(String scenesText) {
        if (scenesText == null || scenesText.isBlank()) return List.of();
        try {
            return objectMapper.readValue(scenesText, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
