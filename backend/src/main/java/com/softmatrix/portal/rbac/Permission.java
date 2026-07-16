package com.softmatrix.portal.rbac;

/** 全局操作级权限目录(9 项)。code = name(),中文名与分组供角色编辑 UI 展示。 */
public enum Permission {
    AGENT_VIEW("Agent 查看", "Agent"),
    AGENT_MANAGE("Agent 管理", "Agent"),
    AGENT_PUBLISH("Agent 发布", "Agent"),
    AGENT_DESIGN("Agent 编排", "Agent"),
    AGENT_RUN("Agent 运行", "Agent"),
    ORG_VIEW("组织查看", "组织"),
    ORG_MANAGE("组织管理", "组织"),
    ROLE_VIEW("角色查看", "角色"),
    ROLE_MANAGE("角色管理", "角色");

    private final String label;
    private final String group;

    Permission(String label, String group) {
        this.label = label;
        this.group = group;
    }

    public String getLabel() { return label; }
    public String getGroup() { return group; }
}
