# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-10
from czghagent_ai.services.technical_scoring import (
    build_technical_scoring_catalog,
    render_technical_scoring_markdown,
    selection_errors,
)
from czghagent_ai.tender_models import SourceSegment


def _segment(locator: str, text: str) -> SourceSegment:
    return SourceSegment(locator_type="TABLE", locator=locator, text=text)


def test_merged_word_table_rows_are_assembled_from_source_without_common_prefix() -> None:
    segments = [
        _segment(
            "表 1 行 22",
            "2.2.4 （1） | 技术 评分标准 （60分） | 实施方案 （33分） | "
            "一、科研项目管理平台建设方案（0-30分） "
            "1.项目全流程管理模块，功能完整并提供同类平台运行截图，得(3-5]分；"
            "内容简略得(1-2]分；无方案或未提供截图，得0分。 "
            "二、项目实施与运维保障方案（0-3分） 包含实施组织和1年运维，得(2-3]分；"
            "内容基本完整得(1-2]分；方案缺失得0分。",
        ),
        _segment(
            "表 1 行 23",
            "2.2.4 （1） | 技术 评分标准 （60分） | 人员配置 （9分） | "
            "①配置1名项目经理，具备系统规划与管理师证书得1分，不提供不得分；"
            "②配置1名技术负责人，具备软件设计师中级资格得1分。",
        ),
        _segment(
            "表 1 行 24",
            "2.2.4 （1） | 技术 评分标准 （60分） | 保障措施 （6分） | "
            "有完整可靠的质量、进度、保密等保障措施，自主赋分[1-6]分。",
        ),
        _segment(
            "表 1 行 25",
            "2.2.4 （1） | 技术 评分标准 （60分） | 培训方案 （6分） | "
            "根据培训内容和现场培训计划进行综合赋分，自主赋分[1-6]分。",
        ),
        _segment(
            "表 1 行 26",
            "2.2.4 （1） | 技术 评分标准 （60分） | 售后服务 （6分） | "
            "根据售后服务体系、响应时间和维护服务等，自主赋分[1-6]分。",
        ),
    ]

    catalog = build_technical_scoring_catalog(segments)
    markdown = render_technical_scoring_markdown(catalog)

    assert len(catalog.clauses) == 5
    assert catalog.total_score == "60分"
    assert "#### 实施方案" in markdown and "* 分值：33分" in markdown
    assert "#### 人员配置" in markdown and "* 分值：9分" in markdown
    assert "#### 保障措施" in markdown
    assert "#### 培训方案" in markdown
    assert "#### 售后服务" in markdown
    assert "2.2.4" not in markdown
    assert "技术 评分标准" not in markdown
    assert "同类平台运行截图" in markdown
    assert "1年运维" in markdown


def test_selection_contract_rejects_missing_duplicate_unknown_and_reordered_ids() -> None:
    catalog = build_technical_scoring_catalog([
        _segment("表1行1", "技术评分标准（10分） | 实施方案（6分） | 方案完整得6分。"),
        _segment("表1行2", "技术评分标准（10分） | 保障措施（4分） | 措施完整得4分。"),
    ])
    first, second = catalog.ordered_ids

    assert selection_errors([first], catalog) == [f"缺少源条款ID：{second}"]
    assert selection_errors([first, first, second], catalog) == [f"条款ID重复：{first}"]
    assert selection_errors([first, "unknown", second], catalog) == ["包含未知条款ID：unknown"]
    assert selection_errors([second, first], catalog) == ["条款ID顺序与招标文件原始顺序不一致"]


def test_commercial_scoring_rows_are_not_added_to_technical_catalog() -> None:
    catalog = build_technical_scoring_catalog([
        _segment("表1行1", "技术评分标准（10分） | 实施方案（10分） | 方案完整得10分。"),
        _segment("表1行2", "商务评分标准（20分） | 企业业绩（20分） | 每项业绩得5分。"),
        _segment("表1行3", "报价评分标准（30分） | 报价得分（30分） | 最低价得30分。"),
    ])

    assert len(catalog.clauses) == 1
    assert "方案完整得10分" in catalog.clauses[0].text
