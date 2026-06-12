package com.xcs.wx.export.enums;

/**
 * 导出任务状态枚举
 *
 * @author xcs
 */
public enum ExportTaskStatus {

    CREATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /**
     * 校验状态转换是否合法
     *
     * @param target 目标状态
     * @return 是否可转换
     */
    public boolean canTransitionTo(ExportTaskStatus target) {
        switch (this) {
            case CREATED:
                return target == QUEUED || target == CANCELLED;
            case QUEUED:
                return target == RUNNING || target == CANCELLED;
            case RUNNING:
                return target == COMPLETED || target == FAILED || target == CANCELLED;
            case FAILED:
                return target == QUEUED;
            case COMPLETED:
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }
}
