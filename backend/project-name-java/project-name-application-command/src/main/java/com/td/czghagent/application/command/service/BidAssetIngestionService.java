// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.model.ParsedDocument;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.DocumentParser;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class BidAssetIngestionService {
    private static final int CHUNK_CHARACTERS = 1_200;
    private static final int CHUNK_OVERLAP = 150;

    private final BidRepository bidRepository;
    private final FileStorage fileStorage;
    private final DocumentParser documentParser;

    public BidAssetIngestionService(BidRepository bidRepository, FileStorage fileStorage,
                                    DocumentParser documentParser) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
        this.documentParser = documentParser;
    }

    /**
     * 素材只在缺少持久化分块时解析。
     *
     * <p><b>Side Effects:</b> 写入素材分块并更新素材状态。</p>
     * <p><b>Error Semantics:</b> 解析失败时保留素材并标记 FAILED。</p>
     */
    public List<BidReferenceChunk> ensureIngested(BidRepository.AssetRecord asset) {
        if ("GALLERY".equals(asset.category())) {
            return List.of();
        }
        List<BidReferenceChunk> existing = bidRepository.listAssetChunks(List.of(asset.id()));
        if (!existing.isEmpty()) {
            return existing;
        }
        bidRepository.markAssetIngestion(asset.id(), "PROCESSING", null);
        try {
            StoredFile file = fileStorage.read(
                    asset.objectKey(), asset.originalFileName(), asset.mediaType());
            ParsedDocument parsed = documentParser.parse(asset.id(), file);
            List<BidReferenceChunk> chunks = createChunks(asset, parsed);
            persist(asset.id(), chunks);
            return chunks;
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            bidRepository.markAssetIngestion(asset.id(), "FAILED", message);
            throw new BusinessException("BID_ASSET_INGESTION_FAILED", message, 502);
        }
    }

    private void persist(String assetId, List<BidReferenceChunk> chunks) {
        bidRepository.replaceAssetChunks(assetId, chunks);
        bidRepository.markAssetIngestion(assetId, "ACTIVE", null);
    }

    private List<BidReferenceChunk> createChunks(
            BidRepository.AssetRecord asset,
            ParsedDocument parsed
    ) {
        List<BidReferenceChunk> chunks = new ArrayList<>();
        String heading = asset.displayName();
        for (ParsedDocument.Evidence evidence : parsed.evidence()) {
            if ("HEADING".equals(evidence.locatorType())) {
                heading = safe(evidence.excerpt());
                continue;
            }
            appendChunks(asset, heading, evidence.locator(), evidence.excerpt(), chunks);
        }
        if (chunks.isEmpty() && parsed.summary() != null && !parsed.summary().isBlank()) {
            appendChunks(asset, "摘要", "摘要", parsed.summary(), chunks);
        }
        if (chunks.isEmpty()) {
            throw new BusinessException("BID_ASSET_EMPTY", "素材中没有可检索文本", 422);
        }
        return List.copyOf(chunks);
    }

    private void appendChunks(BidRepository.AssetRecord asset, String heading,
                              String locator, String value, List<BidReferenceChunk> output) {
        String text = value == null ? "" : value.trim();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_CHARACTERS);
            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                output.add(new BidReferenceChunk(
                        UUID.randomUUID().toString(), asset.id(), asset.category(), asset.displayName(),
                        output.size(), safe(heading), safe(locator), content, sha256(content), content.length()));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank() ? "素材解析失败" : message;
        return value.substring(0, Math.min(1000, value.length()));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
