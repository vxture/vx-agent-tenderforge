// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.repository;

import com.td.czghagent.domain.model.AuditEvent;

/**
 * 审计写入。
 *
 * <p><strong>只追加，不更新也不删除。</strong>审计流的更正是补偿事件，从不是修改——
 * 一条可以被改写的审计记录回答不了「当时到底发生了什么」，而那正是它存在的唯一理由。
 * 接口上不暴露 update / delete，是让这条纪律在类型层面成立，而不是靠约定。
 */
public interface AuditRepository {

    void append(AuditEvent event);
}
