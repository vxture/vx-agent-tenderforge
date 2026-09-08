// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.infrastructure.export;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.BidDocumentExporter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.document-service.enabled", havingValue = "false", matchIfMissing = true)
public class OpenXmlBidDocumentExporter implements BidDocumentExporter {

    @Override
    public byte[] renderDocx(BidDocument bid, List<BidWorkspace.OutlineNode> outline,
                             List<BidWorkspace.Chapter> chapters) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            createCover(document, bid);
            document.createParagraph().setPageBreak(true);
            Map<String, BidWorkspace.Chapter> chapterByNode = new HashMap<>();
            chapters.forEach(chapter -> chapterByNode.put(chapter.outlineNodeId(), chapter));
            Map<String, String> labels = outlineLabels(outline);
            for (BidWorkspace.OutlineNode node : outline) {
                addHeading(document, labels.get(node.id()), node.level());
                BidWorkspace.Chapter chapter = chapterByNode.get(node.id());
                if (chapter != null) {
                    String body = htmlToText(chapter.content());
                    if (body.isBlank()) {
                        addBody(document, "【本章尚未编制】");
                    } else {
                        body.lines().filter(line -> !line.isBlank()).forEach(line -> addBody(document, line));
                    }
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("BID_EXPORT_RENDER_FAILED", "Word成果文件生成失败", 500);
        }
    }

    private void createCover(XWPFDocument document, BidDocument bid) {
        for (int index = 0; index < 8; index++) {
            document.createParagraph();
        }
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setText(bid.title());
        titleRun.setBold(true);
        titleRun.setFontFamily("Microsoft YaHei");
        titleRun.setFontSize(24);
        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtitleRun = subtitle.createRun();
        subtitleRun.setText("投标文件（" + ("BLIND".equals(bid.biddingMode()) ? "暗标" : "明标") + "）");
        subtitleRun.setFontFamily("Microsoft YaHei");
        subtitleRun.setFontSize(14);
    }

    private void addHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + Math.min(3, Math.max(1, level)));
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(level == 1 ? 18 : level == 2 ? 16 : 14);
    }

    private void addBody(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setFirstLineIndent(420);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(text.strip());
        run.setFontFamily("SimSun");
        run.setFontSize(12);
    }

    private String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                @Override
                public void handleText(char[] data, int position) {
                    output.append(data);
                }

                @Override
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag.isBlock() && !output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
                        output.append('\n');
                    }
                }

                @Override
                public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag == HTML.Tag.BR) {
                        output.append('\n');
                    }
                }

                @Override
                public void handleEndTag(HTML.Tag tag, int position) {
                    if (tag.isBlock() && !output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
                        output.append('\n');
                    }
                }
            }, true);
            return output.toString().trim();
        } catch (IOException exception) {
            return html;
        }
    }

    private Map<String, String> outlineLabels(List<BidWorkspace.OutlineNode> outline) {
        Map<String, List<BidWorkspace.OutlineNode>> siblings = new HashMap<>();
        outline.forEach(node -> siblings.computeIfAbsent(
                node.parentId() == null ? "__root__" : node.parentId(), ignored -> new ArrayList<>())
                .add(node));
        Map<String, String> result = new HashMap<>();
        siblings.values().forEach(nodes -> {
            nodes.sort(Comparator.comparingInt(BidWorkspace.OutlineNode::sortOrder)
                    .thenComparing(BidWorkspace.OutlineNode::id));
            for (int index = 0; index < nodes.size(); index++) {
                BidWorkspace.OutlineNode node = nodes.get(index);
                result.put(node.id(), numberPrefix(node.level(), index + 1)
                        + stripNumberPrefix(node.title(), node.level()));
            }
        });
        return result;
    }

    private String numberPrefix(int level, int index) {
        String number = chineseNumber(index);
        return switch (level) {
            case 1 -> "第" + number + "章 ";
            case 2 -> number + "、";
            default -> "（" + number + "）";
        };
    }

    private String chineseNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value <= 0) {
            throw new IllegalArgumentException("outline sibling index must be positive");
        }
        if (value < 10) {
            return digits[value];
        }
        if (value < 20) {
            return "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        if (value < 100) {
            return digits[value / 10] + "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        if (value < 1_000) {
            int remainder = value % 100;
            String suffix = remainder == 0 ? ""
                    : (remainder < 10 ? "零" : "") + chineseNumber(remainder);
            return digits[value / 100] + "百" + suffix;
        }
        if (value < 10_000) {
            int remainder = value % 1_000;
            String suffix = remainder == 0 ? ""
                    : (remainder < 100 ? "零" : "") + chineseNumber(remainder);
            return digits[value / 1_000] + "千" + suffix;
        }
        return Integer.toString(value);
    }

    private String stripNumberPrefix(String title, int level) {
        String value = title.trim();
        return switch (level) {
            case 1 -> value.replaceFirst("^第[〇零一二三四五六七八九十百千两\\d]+章[、.．\\s]*", "");
            case 2 -> value.replaceFirst("^[〇零一二三四五六七八九十百千两\\d]+[、.．]\\s*", "");
            default -> value.replaceFirst("^[（(][〇零一二三四五六七八九十百千两\\d]+[）)]\\s*", "");
        };
    }
}
