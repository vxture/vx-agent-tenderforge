package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
class BidAssetCommandService {
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".doc", ".docx", ".pdf", ".xlsx", ".xlsm", ".csv", ".txt", ".md");
    private static final Set<String> ASSET_CATEGORIES = Set.of(
            "TEMPLATE", "OUTLINE", "GALLERY");

    private final BidRepository bidRepository;
    private final FileStorage fileStorage;
    private final BidAssetIngestionService assetIngestionService;

    BidAssetCommandService(BidRepository bidRepository, FileStorage fileStorage,
                           BidAssetIngestionService assetIngestionService) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
        this.assetIngestionService = assetIngestionService;
    }

    BidReferenceAsset uploadAsset(String category, String fileName, String mediaType,
                                  byte[] content, OperationContext context) {
        validateAsset(category, fileName, content);
        StoredFile stored = fileStorage.store(
                "users/" + context.user().id() + "/bid-assets",
                fileName, mediaType, content);
        String id = UUID.randomUUID().toString();
        BidRepository.AssetRecord asset = new BidRepository.AssetRecord(
                id, context.user().id(), category, displayName(fileName), fileName,
                stored.objectKey(), mediaType, stored.size(), stored.contentHash(),
                "GALLERY".equals(category) ? "ACTIVE" : "PROCESSING");
        try {
            bidRepository.insertAsset(asset);
        } catch (RuntimeException exception) {
            fileStorage.delete(stored.objectKey());
            throw exception;
        }
        if (!"GALLERY".equals(category)) {
            assetIngestionService.ensureIngested(asset);
        }
        return bidRepository.listAssets(context.user().id(), category, null).stream()
                .filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    void removeAsset(String assetId, OperationContext context) {
        BidRepository.AssetRecord asset = bidRepository
                .findAsset(assetId, context.user().id())
                .orElseThrow(() -> new BusinessException(
                        "BID_ASSET_NOT_FOUND", "素材不存在", 404));
        if (!bidRepository.removeAsset(assetId, context.user().id())) {
            throw new BusinessException("BID_ASSET_NOT_FOUND", "素材不存在", 404);
        }
        fileStorage.delete(asset.objectKey());
    }

    private void validateAsset(String category, String fileName, byte[] content) {
        if (!ASSET_CATEGORIES.contains(category) || fileName == null
                || content == null || content.length == 0) {
            throw new BusinessException("BID_ASSET_INVALID", "素材类别或文件无效", 400);
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        boolean supported = "GALLERY".equals(category)
                ? lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                : DOCUMENT_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!supported || content.length > 50L * 1024 * 1024) {
            throw new BusinessException(
                    "BID_ASSET_INVALID", "素材格式不支持或超过50MB", 400);
        }
    }

    private String displayName(String fileName) {
        int index = fileName.lastIndexOf('.');
        int end = index > 0 ? index : fileName.length();
        return fileName.substring(0, Math.min(end, 160));
    }
}
