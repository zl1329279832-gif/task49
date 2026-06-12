package com.xcs.wx.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.xcs.wx.constant.ChatRoomConstant;
import com.xcs.wx.constant.DataSourceType;
import com.xcs.wx.constant.ExportTaskStatus;
import com.xcs.wx.domain.ExportTask;
import com.xcs.wx.domain.Msg;
import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.ExportMsgVO;
import com.xcs.wx.domain.vo.MsgVO;
import com.xcs.wx.mapping.MsgMapping;
import com.xcs.wx.msg.MsgStrategy;
import com.xcs.wx.msg.MsgStrategyFactory;
import com.xcs.wx.protobuf.MsgProto;
import com.xcs.wx.repository.ContactRepository;
import com.xcs.wx.repository.MsgRepository;
import com.xcs.wx.service.ExportTaskExecutor;
import com.xcs.wx.service.ExportTaskManager;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystems;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 导出任务执行器实现
 *
 * @author wx-dump-4j
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskExecutorImpl implements ExportTaskExecutor {

    private final ExportTaskManager exportTaskManager;
    private final MsgRepository msgRepository;
    private final MsgMapping msgMapping;
    private final ContactRepository contactRepository;

    /**
     * 分批大小
     */
    private static final int BATCH_SIZE = 500;

    @Override
    @Async("exportTaskExecutor")
    public void executeExport(String taskId) {
        ExportTask task = exportTaskManager.getTask(taskId);
        try {
            // 状态转换 PENDING -> RUNNING
            exportTaskManager.updateStatus(taskId, ExportTaskStatus.RUNNING);

            ExportTaskCreateDTO params = task.getParams();
            String wxId = task.getWxId();
            List<String> talkers = params.getTalkers();
            Long startTime = params.getStartTime();
            Long endTime = params.getEndTime();
            List<Integer> msgTypes = params.getMsgTypes();

            // 1. 统计总消息数
            int totalCount = 0;
            for (String talker : talkers) {
                if (task.isCancelRequested()) {
                    handleCancellation(task);
                    return;
                }
                totalCount += msgRepository.countMsgByTalker(wxId, talker, startTime, endTime);
            }
            task.setTotalMessages(totalCount);

            if (totalCount == 0) {
                log.info("Task [{}] no messages found for the given criteria", taskId);
                if (task.isCancelRequested()) {
                    handleCancellation(task);
                    return;
                }
                // 创建空文件
                String filePath = prepareFilePath(task);
                task.setFilePath(filePath);
                try (ExcelWriter excelWriter = EasyExcel.write(filePath, ExportMsgVO.class).build()) {
                    WriteSheet writeSheet = EasyExcel.writerSheet("聊天记录").build();
                    excelWriter.write(Collections.emptyList(), writeSheet);
                }
                task.markCompleted();
                if (task.getStatus() == ExportTaskStatus.COMPLETED) {
                    exportTaskManager.generateDownloadToken(taskId);
                }
                return;
            }

            // 2. 准备导出文件
            String filePath = prepareFilePath(task);
            task.setFilePath(filePath);

            // 3. 分批读取并写入Excel
            try (ExcelWriter excelWriter = EasyExcel.write(filePath, ExportMsgVO.class).build()) {
                WriteSheet writeSheet = EasyExcel.writerSheet("聊天记录").build();

                for (String talker : talkers) {
                    if (task.isCancelRequested()) {
                        handleCancellation(task);
                        return;
                    }
                    exportTalkerMessages(task, wxId, talker, startTime, endTime, msgTypes, excelWriter, writeSheet);
                }
            }

            // 4. 检查最终取消状态
            if (task.isCancelRequested()) {
                handleCancellation(task);
                return;
            }

            // 5. 标记完成
            task.markCompleted();
            // markCompleted 内部可能因并发的取消请求转为 CANCELLED
            if (task.getStatus() == ExportTaskStatus.COMPLETED) {
                exportTaskManager.generateDownloadToken(taskId);
                log.info("Task [{}] completed successfully, file=[{}]", taskId, filePath);
            }

        } catch (Exception e) {
            if (task.isCancelRequested()) {
                handleCancellation(task);
            } else {
                log.error("Task [{}] execution failed", taskId, e);
                task.markFailed(e.getMessage());
                // 清理失败时的临时文件
                cleanupFile(task);
            }
        }
    }

    /**
     * 导出单个会话的消息（分批）
     */
    private void exportTalkerMessages(ExportTask task, String wxId, String talker,
                                       Long startTime, Long endTime, List<Integer> msgTypes,
                                       ExcelWriter excelWriter, WriteSheet writeSheet) {
        long maxSequence = Long.MAX_VALUE;
        boolean hasTypeFilter = msgTypes != null && !msgTypes.isEmpty();

        while (true) {
            // 检查取消标志
            if (task.isCancelRequested()) {
                return;
            }

            // 分批查询（原始数据，用于游标推进和进度统计）
            List<Msg> rawBatch = msgRepository.queryMsgBatch(wxId, talker, maxSequence, BATCH_SIZE, startTime, endTime);
            if (rawBatch.isEmpty()) {
                break;
            }

            // 先更新游标（基于原始批次最后一条，sequence 降序排列）
            Msg lastMsg = rawBatch.get(rawBatch.size() - 1);
            maxSequence = lastMsg.getSequence();

            // 用原始数量更新进度（与 countMsgByTalker 的无类型过滤总数对齐）
            task.addProcessed(rawBatch.size());

            // 消息类型过滤（仅影响写入 Excel 的数据）
            List<Msg> filteredBatch = rawBatch;
            if (hasTypeFilter) {
                filteredBatch = rawBatch.stream()
                        .filter(msg -> msgTypes.contains(msg.getType()))
                        .collect(Collectors.toList());
            }

            if (!filteredBatch.isEmpty()) {
                // 转换为MsgVO并应用策略
                List<MsgVO> msgVOList = convertBatch(filteredBatch, talker, wxId);
                // 转换为ExportMsgVO并写入
                List<ExportMsgVO> exportList = msgMapping.convertToExportMsgVO(msgVOList);
                excelWriter.write(exportList, writeSheet);
            }

            // 如果原始批次不满 BATCH_SIZE，说明已经到底了
            if (rawBatch.size() < BATCH_SIZE) {
                break;
            }
        }
    }

    /**
     * 批量转换消息并应用策略
     */
    private List<MsgVO> convertBatch(List<Msg> batch, String talker, String taskWxId) {
        return msgMapping.convert(batch).stream()
                .sorted(Comparator.comparing(MsgVO::getCreateTime))
                .peek(msgVO -> {
                    msgVO.setWxId(getChatWxId(talker, msgVO, taskWxId));
                    msgVO.setStrCreateTime(DateUtil.formatDateTime(new Date(msgVO.getCreateTime() * 1000)));
                    MsgStrategy strategy = MsgStrategyFactory.getStrategy(msgVO.getType(), msgVO.getSubType());
                    if (strategy != null) {
                        strategy.process(msgVO);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取对话人Id
     */
    private String getChatWxId(String talker, MsgVO msgVO, String taskWxId) {
        if (msgVO.getIsSender() != null && msgVO.getIsSender() == 1) {
            // 直接使用任务创建时捕获的 wxId，避免在异步线程中调用 currentUser()
            return taskWxId;
        }
        try {
            if (talker.endsWith(ChatRoomConstant.CHATROOM_SUFFIX)) {
                MsgProto.MessageBytesExtra messageBytesExtra = MsgProto.MessageBytesExtra.parseFrom(msgVO.getBytesExtra());
                List<MsgProto.SubMessage2> message2List = messageBytesExtra.getMessage2List();
                for (MsgProto.SubMessage2 subMessage2 : message2List) {
                    if (subMessage2.getField1() == 1) {
                        return subMessage2.getField2();
                    }
                }
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to obtain the conversationalist Id", e);
        }
        return talker;
    }

    /**
     * 准备导出文件路径
     */
    private String prepareFilePath(ExportTask task) {
        String separator = FileSystems.getDefault().getSeparator();
        String dirPath = System.getProperty("user.dir") + separator + "data" + separator + "export";
        FileUtil.mkdir(dirPath);
        return dirPath + separator + task.getFileName();
    }

    /**
     * 处理取消
     */
    private void handleCancellation(ExportTask task) {
        log.info("Task [{}] cancelled", task.getTaskId());
        cleanupFile(task);
        task.markCancelled();
    }

    /**
     * 清理临时文件
     */
    private void cleanupFile(ExportTask task) {
        if (task.getFilePath() != null) {
            try {
                java.io.File file = new java.io.File(task.getFilePath());
                if (file.exists()) {
                    FileUtil.del(file);
                    log.info("Cleaned up temp file: {}", task.getFilePath());
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup file: {}", task.getFilePath(), e);
            }
        }
    }
}
