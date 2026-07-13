// agent/AgentStatus.java
package com.softmatrix.portal.agent;

import java.util.Set;

public enum AgentStatus {
    DRAFT,
    PUBLISHED,
    DISABLED;

    /** 目标状态 -> 允许的来源状态集合。 */
    public boolean canTransitionTo(AgentStatus target) {
        return switch (target) {
            case PUBLISHED -> this == DRAFT || this == DISABLED; // 发布 / 重新启用
            case DISABLED  -> this == PUBLISHED;                 // 停用
            case DRAFT     -> this == PUBLISHED;                 // 撤回
        };
    }

    static Set<AgentStatus> all() { return Set.of(DRAFT, PUBLISHED, DISABLED); }
}
