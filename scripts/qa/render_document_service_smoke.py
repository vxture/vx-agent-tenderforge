# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-03
import argparse
from pathlib import Path

from czghagent_ai.document_models import DocumentRenderRequest
from czghagent_ai.services.document_renderer import DocumentRenderer


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    body_seed = (
        "本方案面向金钼生产运营场景，围绕数据治理、知识增强、大模型推理、业务协同和安全运维构建闭环能力。"
        "平台对关键指标实行统一口径管理，以可追溯证据支撑每项技术响应，并通过人工复核确保输出准确、连续和可执行。"
        "实施过程采用分阶段交付和量化验收机制，形成需求、设计、开发、测试、上线与持续优化的完整质量链路。"
    )
    paragraphs = "".join(
        f"<h4>{index + 1}. 关键能力与实施控制</h4><p>{body_seed * 2}</p>"
        for index in range(51)
    )
    content = (
        "<p>技术路线以业务价值为主线，确保系统架构、模型能力和实施组织相互支撑。</p>"
        '<p data-table-title="true">表 1 前置代理服务器资源配置</p>'
        "<table>"
        "<thead><tr><th>设备</th><th>处理器</th><th>内存</th><th>存储</th></tr></thead>"
        "<tbody><tr><td>前置代理服务器</td><td>8核</td><td>64GB</td><td>300GB</td></tr></tbody></table>"
        '<p data-table-note="true">注：表中配置为最低技术要求。</p>'
        + paragraphs
    )
    request = DocumentRenderRequest.model_validate({
        "bidId": "smoke-bid",
        "title": "基于大模型的金钼生产运营辅助智能决策项目",
        "biddingMode": "BLIND",
        "targetPages": 20,
        "outline": [
            {"id": "l1", "parentId": None, "level": 1, "title": "技术实施总体方案", "sortOrder": 0},
            {"id": "l2", "parentId": "l1", "level": 2, "title": "系统总体技术路线", "sortOrder": 1},
            {"id": "l3", "parentId": "l2", "level": 3, "title": "大模型决策能力建设", "sortOrder": 2},
        ],
        "chapters": [{
            "id": "chapter-1", "outlineNodeId": "l3",
            "title": "大模型决策能力建设", "content": content,
        }],
        "forbiddenTerms": ["16G", "16GB"],
    })
    docx, qa = DocumentRenderer().render(request)
    (output_dir / "tender-document-smoke.docx").write_bytes(docx)
    (output_dir / "qa.json").write_text(
        qa.model_dump_json(by_alias=True, indent=2), encoding="utf-8"
    )
    if qa.status != "PASSED":
        raise SystemExit(qa.summary)


if __name__ == "__main__":
    main()
