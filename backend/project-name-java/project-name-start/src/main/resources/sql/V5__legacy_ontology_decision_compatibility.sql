-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-30

UPDATE planning_decision
SET decision_code = CASE name
    WHEN '编制任务' THEN 'compilation_task'
    WHEN '本项目编制任务' THEN 'compilation_task'
    WHEN '规划期限' THEN 'planning_period'
    WHEN '本项目规划期限' THEN 'planning_period'
    WHEN '村庄分类' THEN 'village_type'
    WHEN '本项目采用的村庄类型' THEN 'village_type'
    WHEN '区域职能' THEN 'development_positioning'
    WHEN '发展定位' THEN 'development_positioning'
    WHEN '村庄发展定位' THEN 'development_positioning'
    WHEN '现状问题优先级' THEN 'issue_priority'
    WHEN '发展目标' THEN 'development_goals'
    WHEN '发展策略' THEN 'development_strategy'
    ELSE decision_code
END
WHERE (decision_code IS NULL OR TRIM(decision_code) = '')
  AND name IN (
      '编制任务', '本项目编制任务', '规划期限', '本项目规划期限',
      '村庄分类', '本项目采用的村庄类型', '区域职能', '发展定位',
      '村庄发展定位', '现状问题优先级', '发展目标', '发展策略'
  );
