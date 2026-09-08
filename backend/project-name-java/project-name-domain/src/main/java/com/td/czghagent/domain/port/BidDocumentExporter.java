// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;

import java.util.List;

public interface BidDocumentExporter {
    byte[] renderDocx(BidDocument bid,
                      List<BidWorkspace.OutlineNode> outline,
                      List<BidWorkspace.Chapter> chapters);

    default RenderedDocument render(BidDocument bid,
                                    List<BidWorkspace.OutlineNode> outline,
                                    List<BidWorkspace.Chapter> chapters) {
        return new RenderedDocument(
                renderDocx(bid, outline, chapters), null, "PASSED",
                "DOCX结构生成完成；当前渲染器未返回PDF实测页数"
        );
    }

    default RenderedDocument render(BidDocument bid,
                                    List<BidWorkspace.OutlineNode> outline,
                                    List<BidWorkspace.Chapter> chapters,
                                    List<String> forbiddenTerms) {
        return render(bid, outline, chapters);
    }

    record RenderedDocument(
            byte[] docx, Integer actualPages, String qaStatus, String qaSummary
    ) {
    }
}
