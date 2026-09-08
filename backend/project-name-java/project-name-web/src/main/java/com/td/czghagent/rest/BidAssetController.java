// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.BidCommandService;
import com.td.czghagent.application.query.service.BidQueryService;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.ApiResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/bid-assets")
public class BidAssetController {

    private final BidCommandService commandService;
    private final BidQueryService queryService;

    public BidAssetController(BidCommandService commandService, BidQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<List<BidReferenceAsset>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryService.assets(category, keyword, RequestIdentity.user(request)),
                RequestIdentity.traceId(request)
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BidReferenceAsset> upload(
            @RequestParam String category,
            @RequestParam MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        String mediaType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        return ApiResponse.success(commandService.uploadAsset(
                        category, file.getOriginalFilename(), mediaType, file.getBytes(),
                        RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> remove(@PathVariable String assetId, HttpServletRequest request) {
        commandService.removeAsset(assetId, RequestIdentity.operation(request));
        return ApiResponse.success(null, RequestIdentity.traceId(request));
    }
}
