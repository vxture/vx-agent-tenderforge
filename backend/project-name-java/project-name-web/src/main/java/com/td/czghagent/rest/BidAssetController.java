// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.BidCommandService;
import com.td.czghagent.application.query.service.BidQueryService;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/bid-assets")
public class BidAssetController {

    private final BidCommandService commandService;
    private final BidQueryService queryService;

    public BidAssetController(BidCommandService commandService, BidQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** 素材库：{@code {items, nextCursor}}，{@code limit} 由服务端钳制到 200（通则 A-3 / A-4）。 */
    @GetMapping
    public CursorPage<BidReferenceAsset> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request
    ) {
        return queryService.assets(category, keyword, limit, cursor, RequestIdentity.user(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BidReferenceAsset upload(
            @RequestParam String category,
            @RequestParam MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        String mediaType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        return commandService.uploadAsset(
                        category, file.getOriginalFilename(), mediaType, file.getBytes(),
                        RequestIdentity.operation(request));
    }

    @DeleteMapping("/{assetId}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String assetId, HttpServletRequest request) {
        commandService.removeAsset(assetId, RequestIdentity.operation(request));
    }
}
