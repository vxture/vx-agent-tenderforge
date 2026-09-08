// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.BidCommandService;
import com.td.czghagent.application.command.service.BidLayoutService;
import com.td.czghagent.application.command.service.BidProductionService;
import com.td.czghagent.application.query.service.BidQueryService;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidSummary;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.BidWorkspaceViews;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.rest.dto.BidRequests;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidCommandService commandService;
    private final BidProductionService productionService;
    private final BidLayoutService layoutService;
    private final BidQueryService queryService;

    public BidController(BidCommandService commandService,
                         BidProductionService productionService,
                         BidLayoutService layoutService,
                         BidQueryService queryService) {
        this.commandService = commandService;
        this.productionService = productionService;
        this.layoutService = layoutService;
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<List<BidSummary>> list(HttpServletRequest request) {
        return ApiResponse.success(
                queryService.list(RequestIdentity.user(request)), RequestIdentity.traceId(request)
        );
    }

    @PostMapping
    public ApiResponse<BidWorkspace> create(@Valid @RequestBody BidRequests.Create body,
                                            HttpServletRequest request) {
        return ApiResponse.success(
                commandService.create(
                        body.writingMethod(), body.title(), body.targetPages(), body.biddingMode(),
                        RequestIdentity.operation(request)
                ),
                RequestIdentity.traceId(request)
        );
    }

    @GetMapping("/{bidId}")
    public ApiResponse<BidWorkspace> get(@PathVariable String bidId, HttpServletRequest request) {
        return ApiResponse.success(
                queryService.workspace(bidId, RequestIdentity.user(request)),
                RequestIdentity.traceId(request)
        );
    }

    @GetMapping("/{bidId}/metadata")
    public ApiResponse<BidWorkspaceViews.Metadata> metadata(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryService.metadata(bidId, RequestIdentity.user(request)),
                RequestIdentity.traceId(request));
    }

    @GetMapping("/{bidId}/outline")
    public ApiResponse<BidWorkspaceViews.OutlineView> outline(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryService.outline(bidId, RequestIdentity.user(request)),
                RequestIdentity.traceId(request));
    }

    @GetMapping("/{bidId}/generation-progress")
    public ApiResponse<BidWorkspaceViews.GenerationProgress> generationProgress(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryService.generationProgress(bidId, RequestIdentity.user(request)),
                RequestIdentity.traceId(request));
    }

    @PatchMapping("/{bidId}/setup")
    public ApiResponse<BidWorkspace> setup(@PathVariable String bidId,
                                           @Valid @RequestBody BidRequests.Setup body,
                                           HttpServletRequest request) {
        return ApiResponse.success(commandService.saveSetup(
                        bidId, body.title(), body.targetPages(), body.biddingMode(), body.revision(),
                        RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping(value = "/{bidId}/source-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BidWorkspace> uploadSource(@PathVariable String bidId,
                                                  @RequestParam MultipartFile file,
                                                  HttpServletRequest request) throws IOException {
        String mediaType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        return ApiResponse.success(commandService.uploadSource(
                        bidId, file.getOriginalFilename(), mediaType, file.getBytes(),
                        RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/interpretation/parse")
    public ResponseEntity<ApiResponse<BidWorkspace>> parseSource(
            @PathVariable String bidId, HttpServletRequest request) {
        ApiResponse<BidWorkspace> response = ApiResponse.success(
                commandService.parseSource(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
        return ResponseEntity.accepted().body(response);
    }

    @PutMapping("/{bidId}/criteria")
    public ApiResponse<BidWorkspace> saveCriteria(@PathVariable String bidId,
                                                  @Valid @RequestBody BidRequests.Criteria body,
                                                  HttpServletRequest request) {
        List<BidCommandService.CriterionInput> items = body.items().stream().map(item ->
                new BidCommandService.CriterionInput(
                        item.id(), item.type(), item.title(), item.description(),
                        item.score(), item.sourceExcerpt(), item.sourceLocator(),
                        item.scope(), item.confidence()
                )).toList();
        return ApiResponse.success(commandService.saveCriteria(
                        bidId, items, body.revision(), RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/interpretation/freeze")
    public ApiResponse<BidWorkspace> freezeInterpretation(
            @PathVariable String bidId,
            @Valid @RequestBody BidRequests.Revision body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productionService.freezeInterpretation(
                        bidId, body.revision(), RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PutMapping("/{bidId}/asset-selections")
    public ApiResponse<BidWorkspace> selectAssets(@PathVariable String bidId,
                                                  @Valid @RequestBody BidRequests.AssetSelections body,
                                                  HttpServletRequest request) {
        return ApiResponse.success(commandService.selectAssets(
                        bidId, body.assetIds(), RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/outline/generate")
    public ResponseEntity<ApiResponse<BidWorkspace>> generateOutline(
            @PathVariable String bidId, HttpServletRequest request) {
        ApiResponse<BidWorkspace> response = ApiResponse.success(
                commandService.generateOutline(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
        return ResponseEntity.accepted().body(response);
    }

    @PutMapping("/{bidId}/outline")
    public ApiResponse<BidWorkspace> saveOutline(@PathVariable String bidId,
                                                 @Valid @RequestBody BidRequests.Outline body,
                                                 HttpServletRequest request) {
        List<BidCommandService.OutlineInput> nodes = body.nodes().stream().map(node ->
                new BidCommandService.OutlineInput(
                        node.clientId(), node.parentClientId(), node.level(),
                        node.title(), node.plannedPages(), node.taskBrief(),
                        node.mustKeywords(), node.scoringPointIds()
                )).toList();
        return ApiResponse.success(commandService.saveOutline(
                        bidId, nodes, body.confirm(), body.revision(), RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/outline/freeze")
    public ApiResponse<BidWorkspace> freezeOutline(
            @PathVariable String bidId,
            @Valid @RequestBody BidRequests.Revision body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productionService.freezeOutline(
                        bidId, body.revision(), RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/content/generate")
    public ResponseEntity<ApiResponse<BidWorkspace>> generateContent(
            @PathVariable String bidId, HttpServletRequest request) {
        ApiResponse<BidWorkspace> response = ApiResponse.success(
                commandService.startGeneration(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/{bidId}/content/generation/pause")
    public ApiResponse<BidWorkspace> pauseContentGeneration(
            @PathVariable String bidId, HttpServletRequest request) {
        return ApiResponse.success(
                commandService.pauseGeneration(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/content/generation/resume")
    public ResponseEntity<ApiResponse<BidWorkspace>> resumeContentGeneration(
            @PathVariable String bidId, HttpServletRequest request) {
        ApiResponse<BidWorkspace> response = ApiResponse.success(
                commandService.resumeGeneration(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{bidId}/generation-events")
    public ApiResponse<List<BidProductionState.GenerationEvent>> generationEvents(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryService.generationProgress(bidId, RequestIdentity.user(request)).recentEvents(),
                RequestIdentity.traceId(request));
    }

    @GetMapping("/{bidId}/chapters/{chapterId}")
    public ApiResponse<BidWorkspaceViews.ChapterDetail> chapter(
            @PathVariable String bidId,
            @PathVariable String chapterId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                queryService.chapter(bidId, chapterId, RequestIdentity.user(request)),
                RequestIdentity.traceId(request));
    }

    @PatchMapping("/{bidId}/chapters/{chapterId}")
    public ApiResponse<BidWorkspaceViews.ChapterDetail> saveChapter(
            @PathVariable String bidId,
            @PathVariable String chapterId,
            @Valid @RequestBody BidRequests.Chapter body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(commandService.saveChapter(
                        bidId, chapterId, body.content(), body.revision(),
                        RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/chapters/{chapterId}/ai-revisions")
    public ApiResponse<BidCommandService.SectionRevisionCandidate> reviseChapter(
            @PathVariable String bidId,
            @PathVariable String chapterId,
            @Valid @RequestBody BidRequests.SectionRevision body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(commandService.reviseChapter(
                        bidId, chapterId, body.mode(), body.selectedHtml(), body.beforeContext(),
                        body.afterContext(), body.instruction(), body.revision(),
                        RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/content/review")
    public ApiResponse<BidWorkspace> reviewContent(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return ApiResponse.success(
                commandService.reviewContent(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/content/freeze")
    public ApiResponse<BidWorkspace> freezeContent(
            @PathVariable String bidId,
            @Valid @RequestBody BidRequests.Revision body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productionService.freezeContent(
                        bidId, body.revision(), RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @PostMapping("/{bidId}/layout-jobs")
    public ApiResponse<BidWorkspace> startLayout(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return ApiResponse.success(
                layoutService.start(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request));
    }

    @GetMapping("/{bidId}/exports")
    public ApiResponse<List<BidExport>> exports(@PathVariable String bidId,
                                                HttpServletRequest request) {
        return ApiResponse.success(
                queryService.exports(bidId, RequestIdentity.user(request)),
                RequestIdentity.traceId(request)
        );
    }

    @PostMapping("/{bidId}/exports")
    public ApiResponse<BidExport> export(@PathVariable String bidId, HttpServletRequest request) {
        return ApiResponse.success(
                commandService.createExport(bidId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request)
        );
    }

    @GetMapping("/{bidId}/exports/latest/download")
    public ResponseEntity<byte[]> downloadLatest(@PathVariable String bidId,
                                                 HttpServletRequest request) {
        StoredFile file = queryService.latestExport(bidId, RequestIdentity.user(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.mediaType()));
        headers.setContentLength(file.size());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
