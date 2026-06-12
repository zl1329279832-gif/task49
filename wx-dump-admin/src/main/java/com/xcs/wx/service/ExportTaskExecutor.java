package com.xcs.wx.service;

/**
 * 导出任务执行器接口
 *
 * @author wx-dump-4j
 */
public interface ExportTaskExecutor {

    /**
     * 异步执行导出任务
     *
     * @param taskId 任务ID
     */
    void executeExport(String taskId);
}
