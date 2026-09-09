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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
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
    public List<BidSummary> list(HttpServletRequest request) {
        return queryService.list(RequestIdentity.user(request));
    }

    @PostMapping
    public BidWorkspace create(@Valid @RequestBody BidRequests.Create body,
                                            HttpServletRequest request) {
        return commandService.create(
                        body.writingMethod(), body.title(), body.targetPages(), body.biddingMode(),
                        RequestIdentity.operation(request)
                );
    }

    @GetMapping("/{bidId}")
    public BidWorkspace get(@PathVariable String bidId, HttpServletRequest request) {
        return queryService.workspace(bidId, RequestIdentity.user(request));
    }

    @GetMapping("/{bidId}/metadata")
    public BidWorkspaceViews.Metadata metadata(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return queryService.metadata(bidId, RequestIdentity.user(request));
    }

    @GetMapping("/{bidId}/outline")
    public BidWorkspaceViews.OutlineView outline(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return queryService.outline(bidId, RequestIdentity.user(request));
    }

    @GetMapping("/{bidId}/generation-progress")
    public BidWorkspaceViews.GenerationProgress generationProgress(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return queryService.generationProgress(bidId, RequestIdentity.user(request));
    }

    @PatchMapping("/{bidId}/setup")
    public BidWorkspace setup(@PathVariable String bidId,
                                           @Valid @RequestBody BidRequests.Setup body,
                                           HttpServletRequest request) {
        return commandService.saveSetup(
                        bidId, body.title(), body.targetPages(), body.biddingMode(), body.revision(),
                        RequestIdentity.operation(request));
    }

    @PostMapping(value = "/{bidId}/source-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BidWorkspace uploadSource(@PathVariable String bidId,
                                                  @RequestParam MultipartFile file,
                                                  HttpServletRequest request) throws IOException {
        String mediaType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        return commandService.uploadSource(
                        bidId, file.getOriginalFilename(), mediaType, file.getBytes(),
                        RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/interpretation/parse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BidWorkspace parseSource(
            @PathVariable String bidId, HttpServletRequest request) {
        return commandService.parseSource(bidId, RequestIdentity.operation(request));
    }

    @PutMapping("/{bidId}/criteria")
    public BidWorkspace saveCriteria(@PathVariable String bidId,
                                                  @Valid @RequestBody BidRequests.Criteria body,
                                                  HttpServletRequest request) {
        List<BidCommandService.CriterionInput> items = body.items().stream().map(item ->
                new BidCommandService.CriterionInput(
                        item.id(), item.type(), item.title(), item.description(),
                        item.score(), item.sourceExcerpt(), item.sourceLocator(),
                        item.scope(), item.confidence()
                )).toList();
        return commandService.saveCriteria(
                        bidId, items, body.revision(), RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/interpretation/freeze")
    public BidWorkspace freezeInterpretation(
            @PathVariable String bidId,
            @Valid @RequestBody BidRequests.Revision body,
            HttpServletRequest request
    ) {
        return productionService.freezeInterpretation(
                        bidId, body.revision(), RequestIdentity.operation(request));
    }

    @PutMapping("/{bidId}/asset-selections")
    public BidWorkspace selectAssets(@PathVariable String bidId,
                                                  @Valid @RequestBody BidRequests.AssetSelections body,
                                                  HttpServletRequest request) {
        return commandService.selectAssets(
                        bidId, body.assetIds(), RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/outline/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BidWorkspace generateOutline(
            @PathVariable String bidId, HttpServletRequest request) {
        return commandService.generateOutline(bidId, RequestIdentity.operation(request));
    }

    @PutMapping("/{bidId}/outline")
    public BidWorkspace saveOutline(@PathVariable String bidId,
                                                 @Valid @RequestBody BidRequests.Outline body,
                                                 HttpServletRequest request) {
        List<BidCommandService.OutlineInput> nodes = body.nodes().stream().map(node ->
                new BidCommandService.OutlineInput(
                        node.clientId(), node.parentClientId(), node.level(),
                        node.title(), node.plannedPages(), node.taskBrief(),
                        node.mustKeywords(), node.scoringPointIds()
                )).toList();
        return commandService.saveOutline(
                        bidId, nodes, body.confirm(), body.revision(), RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/outline/freeze")
    public BidWorkspace freezeOutline(
            @PathVariable String bidId,
            @Valid @RequestBody BidRequests.Revision body,
            HttpServletRequest request
    ) {
        return productionService.freezeOutline(
                        bidId, body.revision(), RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/content/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BidWorkspace generateContent(
            @PathVariable String bidId, HttpServletRequest request) {
        return commandService.startGeneration(bidId, RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/content/generation/pause")
    public BidWorkspace pauseContentGeneration(
            @PathVariable String bidId, HttpServletRequest request) {
        return commandService.pauseGeneration(bidId, RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/content/generation/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BidWorkspace resumeContentGeneration(
            @PathVariable String bidId, HttpServletRequest request) {
        return commandService.resumeGeneration(bidId, RequestIdentity.operation(request));
    }

    @GetMapping("/{bidId}/generation-events")
    public List<BidProductionState.GenerationEvent> generationEvents(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return queryService.generationProgress(bidId, RequestIdentity.user(request)).recentEvents();
    }

    @GetMapping("/{bidId}/chapters/{chapterId}")
    public BidWorkspaceViews.ChapterDetail chapter(
            @PathVariable String bidId,
            @PathVariable String chapterId,
            HttpServletRequest request
    ) {
        return queryService.chapter(bidId, chapterId, RequestIdentity.user(request));
    }

    @PatchMapping("/{bidId}/chapters/{chapterId}")
    public BidWorkspaceViews.ChapterDetail saveChapter(
            @PathVariable String bidId,
            @PathVariable String chapterId,
            @Valid @RequestBody BidRequests.Chapter body,
            HttpServletRequest request
    ) {
        return commandService.saveChapter(
                        bidId, chapterId, body.content(), body.revision(),
                        RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/chapters/{chapterId}/ai-revisions")
    public BidCommandService.SectionRevisionCandidate reviseChapter(
            @PathVariable String bidId,
            @PathVariable String chapterId,
            @Valid @RequestBody BidRequests.SectionRevision body,
            HttpServletRequest request
    ) {
        return commandService.reviseChapter(
                        bidId, chapterId, body.mode(), body.selectedHtml(), body.beforeContext(),
                        body.afterContext(), body.instruction(), body.revision(),
                        RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/content/review")
    public BidWorkspace reviewContent(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return commandService.reviewContent(bidId, RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/content/freeze")
    public BidWorkspace freezeContent(
            @PathVariable String bidId,
            @Valid @RequestBody BidRequests.Revision body,
            HttpServletRequest request
    ) {
        return productionService.freezeContent(
                        bidId, body.revision(), RequestIdentity.operation(request));
    }

    @PostMapping("/{bidId}/layout-jobs")
    public BidWorkspace startLayout(
            @PathVariable String bidId, HttpServletRequest request
    ) {
        return layoutService.start(bidId, RequestIdentity.operation(request));
    }

    @GetMapping("/{bidId}/exports")
    public List<BidExport> exports(@PathVariable String bidId,
                                                HttpServletRequest request) {
        return queryService.exports(bidId, RequestIdentity.user(request));
    }

    @PostMapping("/{bidId}/exports")
    public BidExport export(@PathVariable String bidId, HttpServletRequest request) {
        return commandService.createExport(bidId, RequestIdentity.operation(request));
    }

    /**
     * 下载一次成果。
     *
     * <p>路径按标识寻址而不是 {@code /exports/latest/download}——「最新」是筛选，
     * 不是资源标识，写进路径段会让资源与视角在 URL 上无法区分（A-2）。
     */
    @GetMapping("/{bidId}/exports/{exportId}/download")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String bidId,
                                                 @PathVariable String exportId,
                                                 HttpServletRequest request) {
        StoredFile file = queryService.exportFile(bidId, exportId, RequestIdentity.user(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.mediaType()));
        headers.setContentLength(file.size());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
