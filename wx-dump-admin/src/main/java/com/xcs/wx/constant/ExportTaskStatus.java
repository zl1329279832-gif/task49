package com.xcs.wx.constant;

/**
 * 导出任务状态枚举
 *
 * @author wx-dump-4j
 */
public enum ExportTaskStatus {

    /**
     * 等待执行
     */
    PENDING,

    /**
     * 正在执行
     */
    RUNNING,

    /**
     * 已完成
     */
    COMPLETED,

    /**
     * 已失败
     */
    FAILED,

    /**
     * 已取消
     */
    CANCELLED;

    /**
     * 校验状态转换是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 是否允许转换
     */
    public static boolean isValidTransition(ExportTaskStatus from, ExportTaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        switch (from) {
            case PENDING:
                return to == RUNNING || to == CANCELLED;
            case RUNNING:
                return to == COMPLETED || to == FAILED || to == CANCELLED;
            case FAILED:
            case CANCELLED:
                return to == PENDING;
            case COMPLETED:
                return false;
            default:
                return false;
        }
    }
}
