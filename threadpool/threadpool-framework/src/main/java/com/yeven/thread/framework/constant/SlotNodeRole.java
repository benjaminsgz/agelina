package com.yeven.thread.framework.constant;

/**
 * 插槽节点角色枚举。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>定义节点职责类型：</b> 区分中间数据修补节点（PATCH）与最终上下文解析出口节点（TERMINAL），便于图编译期和运行期应用不同的处理策略。</li>
 * </ul>
 */
public enum SlotNodeRole {
    /**
     * 写入一个或多个插槽的中间计算步骤节点。
     */
    PATCH,
    /**
     * 标志图执行结束并负责构造最终结果的终结点。
     */
    TERMINAL
}
