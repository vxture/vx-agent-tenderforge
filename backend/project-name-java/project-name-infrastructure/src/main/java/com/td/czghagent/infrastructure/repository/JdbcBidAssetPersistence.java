package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class JdbcBidAssetPersistence {
    private final JdbcTemplate jdbcTemplate;

    JdbcBidAssetPersistence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertAsset(BidRepository.AssetRecord asset) {
        jdbcTemplate.update("""
                INSERT INTO bid_reference_asset(
                    id, owner_id, category, display_name, original_file_name,
                    object_key, media_type, file_size, content_hash, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, asset.id(), asset.ownerId(), asset.category(), asset.displayName(),
                asset.originalFileName(), asset.objectKey(), asset.mediaType(), asset.fileSize(),
                asset.contentHash(), asset.status());
    }

    List<BidReferenceAsset> listAssets(String ownerId, String category, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM bid_reference_asset WHERE owner_id = ? AND status = 'ACTIVE'
                """);
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND LOWER(display_name) LIKE ?");
            args.add("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), BidJdbcMappers.ASSET, args.toArray());
    }

    Optional<BidRepository.AssetRecord> findAsset(String assetId, String ownerId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_reference_asset WHERE id = ? AND owner_id = ? AND status = 'ACTIVE'
                """, (rs, row) -> new BidRepository.AssetRecord(
                rs.getString("id"), rs.getString("owner_id"), rs.getString("category"),
                rs.getString("display_name"), rs.getString("original_file_name"),
                rs.getString("object_key"), rs.getString("media_type"), rs.getLong("file_size"),
                rs.getString("content_hash"), rs.getString("status")), assetId, ownerId)
                .stream().findFirst();
    }

    void replaceAssetChunks(String assetId, List<BidReferenceChunk> chunks) {
        jdbcTemplate.update("DELETE FROM bid_asset_chunk WHERE asset_id = ?", assetId);
        for (BidReferenceChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO bid_asset_chunk(
                        id, asset_id, chunk_index, heading, source_locator,
                        content, content_hash, character_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, chunk.id(), assetId, chunk.chunkIndex(), chunk.heading(),
                    chunk.sourceLocator(), chunk.content(), chunk.contentHash(), chunk.characterCount());
        }
    }

    List<BidReferenceChunk> listAssetChunks(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(assetIds.size(), "?"));
        String sql = """
                SELECT c.*, a.category, a.display_name
                FROM bid_asset_chunk c JOIN bid_reference_asset a ON a.id = c.asset_id
                WHERE c.asset_id IN (%s) ORDER BY c.asset_id, c.chunk_index, c.id
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, (rs, row) -> new BidReferenceChunk(
                rs.getString("id"), rs.getString("asset_id"), rs.getString("category"),
                rs.getString("display_name"), rs.getInt("chunk_index"), rs.getString("heading"),
                rs.getString("source_locator"), rs.getString("content"), rs.getString("content_hash"),
                rs.getInt("character_count")), assetIds.toArray());
    }

    void markAssetIngestion(String assetId, String status, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE bid_reference_asset SET status = ?, ingestion_error = ?,
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, status, errorMessage, assetId);
    }

    boolean removeAsset(String assetId, String ownerId) {
        return jdbcTemplate.update("""
                UPDATE bid_reference_asset SET status = 'REMOVED', updated_at = CURRENT_TIMESTAMP,
                    revision = revision + 1 WHERE id = ? AND owner_id = ? AND status = 'ACTIVE'
                """, assetId, ownerId) == 1;
    }
}
