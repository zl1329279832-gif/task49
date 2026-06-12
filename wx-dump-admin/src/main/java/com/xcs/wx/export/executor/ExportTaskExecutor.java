package com.xcs.wx.export.executor;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.xcs.wx.constant.ChatRoomConstant;
import com.xcs.wx.constant.DataSourceType;
import com.xcs.wx.domain.Msg;
import com.xcs.wx.domain.vo.ExportChatRoomVO;
import com.xcs.wx.domain.vo.ExportContactVO;
import com.xcs.wx.domain.vo.ExportMsgVO;
import com.xcs.wx.domain.vo.MsgVO;
import com.xcs.wx.export.enums.ExportTaskStatus;
import com.xcs.wx.export.enums.ExportType;
import com.xcs.wx.export.model.ExportTask;
import com.xcs.wx.mapping.MsgMapping;
import com.xcs.wx.msg.MsgStrategy;
import com.xcs.wx.msg.MsgStrategyFactory;
import com.xcs.wx.protobuf.ChatRoomProto;
import com.xcs.wx.protobuf.MsgProto;
import com.xcs.wx.repository.ChatRoomRepository;
import com.xcs.wx.repository.ContactRepository;
import com.xcs.wx.repository.HardLinkImageAttributeRepository;
import com.xcs.wx.repository.MsgRepository;
import com.xcs.wx.service.UserService;
import com.xcs.wx.util.DSNameUtil;
import com.xcs.wx.util.DirUtil;
import com.xcs.wx.util.ImgDecoderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导出任务异步执行器
 *
 * @author xcs
 */
@Slf4j
@Component
public class ExportTaskExecutor {

    private static final int BATCH_SIZE = 1000;

    private final MsgRepository msgRepository;
    private final MsgMapping msgMapping;
    private final ContactRepository contactRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final HardLinkImageAttributeRepository hardLinkImageAttributeRepository;
    private final UserService userService;
    private final ThreadPoolTaskExecutor executor;

    public ExportTaskExecutor(MsgRepository msgRepository,
                              MsgMapping msgMapping,
                              ContactRepository contactRepository,
                              ChatRoomRepository chatRoomRepository,
                              HardLinkImageAttributeRepository hardLinkImageAttributeRepository,
                              UserService userService,
                              @Qualifier("exportTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.msgRepository = msgRepository;
        this.msgMapping = msgMapping;
        this.contactRepository = contactRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.hardLinkImageAttributeRepository = hardLinkImageAttributeRepository;
        this.userService = userService;
        this.executor = executor;
    }

    /**
     * 提交任务到线程池执行
     */
    public void submitTask(ExportTask task, Consumer<ExportTask> onComplete) {
        executor.submit(() -> {
            // 设置线程级wxId覆盖，确保RepositoryAspect使用正确的数据源
            DSNameUtil.setOverrideWxId(task.getWxId());
            try {
                task.setStatus(ExportTaskStatus.RUNNING);
                task.setUpdatedAt(System.currentTimeMillis());

                switch (task.getExportType()) {
                    case MSG:
                        executeMsgExport(task);
                        break;
                    case CONTACT:
                        executeContactExport(task);
                        break;
                    case CHATROOM:
                        executeChatRoomExport(task);
                        break;
                    default:
                        throw new IllegalArgumentException("不支持的导出类型: " + task.getExportType());
                }

                if (!task.isCancelRequested()) {
                    task.setStatus(ExportTaskStatus.COMPLETED);
                    task.setProgress(100);
                    task.setCompletedAt(System.currentTimeMillis());
                }
            } catch (Exception e) {
                log.error("导出任务失败: {}", task.getTaskId(), e);
                if (!task.isCancelRequested()) {
                    task.setStatus(ExportTaskStatus.FAILED);
                    task.setErrorMessage(e.getMessage());
                }
            } finally {
                task.setUpdatedAt(System.currentTimeMillis());
                DSNameUtil.clearOverrideWxId();
                DynamicDataSourceContextHolder.clear();
                onComplete.accept(task);
            }
        });
    }

    /**
     * 执行消息导出（分批处理）
     */
    private void executeMsgExport(ExportTask task) {
        String wxId = task.getWxId();

        // 解析会话昵称
        String nickname = contactRepository.getContactNickname(task.getTalker());
        if (StrUtil.isBlank(nickname)) {
            nickname = task.getTalker();
        }
        task.setTalkerNickname(nickname);
        task.setProgress(5);

        // 准备导出文件
        String fileName = nickname + "_" + task.getTaskId().substring(0, 8) + ".xlsx";
        String filePath = DirUtil.getExportDir(fileName);
        FileUtil.mkdir(new File(filePath).getParent());

        // 收集图片消息用于媒体打包
        List<MsgVO> mediaMessages = task.isIncludeMedia() ? new ArrayList<>() : null;

        // 获取所有MSG分片数据库
        List<String> msgDbList = DataSourceType.getMsgDb(wxId);
        if (msgDbList.isEmpty()) {
            task.setProgress(100);
            task.setFilePath(filePath);
            // 创建空Excel
            EasyExcel.write(filePath, ExportMsgVO.class).sheet("sheet1").doWrite(Collections.emptyList());
            return;
        }

        // 使用ExcelWriter增量写入
        try (ExcelWriter excelWriter = EasyExcel.write(filePath, ExportMsgVO.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("sheet1").build();
            int totalShards = msgDbList.size();
            int processedShards = 0;

            for (String poolName : msgDbList) {
                if (task.isCancelRequested()) {
                    return;
                }

                // 分页读取该分片
                int offset = 0;
                List<Msg> batch;
                do {
                    if (task.isCancelRequested()) {
                        return;
                    }

                    DynamicDataSourceContextHolder.push(poolName);
                    try {
                        batch = msgRepository.exportMsgBatch(
                                task.getTalker(), wxId,
                                task.getStartTime(), task.getEndTime(),
                                task.getMsgTypes(), offset, BATCH_SIZE);
                    } finally {
                        DynamicDataSourceContextHolder.clear();
                    }

                    if (!batch.isEmpty()) {
                        List<MsgVO> msgVOList = processMsgBatch(batch, task.getTalker(), wxId);
                        List<ExportMsgVO> exportBatch = msgMapping.convertToExportMsgVO(msgVOList);
                        excelWriter.write(exportBatch, sheet);

                        // 收集含图片的消息
                        if (mediaMessages != null) {
                            for (MsgVO msgVO : msgVOList) {
                                if (StrUtil.isNotBlank(msgVO.getImgMd5()) || StrUtil.isNotBlank(msgVO.getImage())) {
                                    mediaMessages.add(msgVO);
                                }
                            }
                        }
                    }
                    offset += BATCH_SIZE;
                } while (batch.size() == BATCH_SIZE);

                processedShards++;
                task.setProgress(10 + (int) (70.0 * processedShards / totalShards));
            }
        }

        task.setProgress(85);

        // 媒体文件打包
        if (task.isIncludeMedia() && mediaMessages != null && !mediaMessages.isEmpty()) {
            filePath = packageWithMedia(task, mediaMessages, filePath, wxId);
        }

        task.setFilePath(filePath);
    }

    /**
     * 处理一批消息（策略解析、时间格式化、wxId设置）
     */
    private List<MsgVO> processMsgBatch(List<Msg> batch, String talker, String wxId) {
        return msgMapping.convert(batch).stream()
                .sorted(Comparator.comparing(MsgVO::getCreateTime))
                .peek(msgVO -> {
                    msgVO.setWxId(getChatWxId(talker, msgVO, wxId));
                    msgVO.setStrCreateTime(DateUtil.formatDateTime(new Date(msgVO.getCreateTime() * 1000)));
                    MsgStrategy strategy = MsgStrategyFactory.getStrategy(msgVO.getType(), msgVO.getSubType());
                    if (strategy != null) {
                        strategy.process(msgVO);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 执行联系人导出
     */
    private void executeContactExport(ExportTask task) {
        task.setProgress(10);

        String fileName = "微信好友_" + task.getTaskId().substring(0, 8) + ".xlsx";
        String filePath = DirUtil.getExportDir(fileName);
        FileUtil.mkdir(new File(filePath).getParent());

        if (task.isCancelRequested()) {
            return;
        }

        task.setProgress(30);
        List<ExportContactVO> contacts = contactRepository.exportContact();

        if (task.isCancelRequested()) {
            return;
        }

        task.setProgress(60);
        EasyExcel.write(filePath, ExportContactVO.class)
                .sheet("sheet1")
                .doWrite(contacts);

        task.setFilePath(filePath);
        task.setProgress(95);
    }

    /**
     * 执行群聊导出
     */
    private void executeChatRoomExport(ExportTask task) {
        task.setProgress(10);

        String fileName = "群聊_" + task.getTaskId().substring(0, 8) + ".xlsx";
        String filePath = DirUtil.getExportDir(fileName);
        FileUtil.mkdir(new File(filePath).getParent());

        if (task.isCancelRequested()) {
            return;
        }

        task.setProgress(30);
        List<ExportChatRoomVO> chatRooms = chatRoomRepository.exportChatRoom();

        // 设置群聊人数
        for (ExportChatRoomVO chatRoom : chatRooms) {
            chatRoom.setMemberCount(handleMembersCount(chatRoom.getRoomData()));
        }

        if (task.isCancelRequested()) {
            return;
        }

        task.setProgress(60);
        EasyExcel.write(filePath, ExportChatRoomVO.class)
                .sheet("sheet1")
                .doWrite(chatRooms);

        task.setFilePath(filePath);
        task.setProgress(95);
    }

    /**
     * 打包媒体文件到zip
     */
    private String packageWithMedia(ExportTask task, List<MsgVO> mediaMessages,
                                    String excelPath, String wxId) {
        String zipFileName = new File(excelPath).getName().replace(".xlsx", ".zip");
        String zipPath = DirUtil.getExportDir(zipFileName);
        String basePath = userService.getBasePath(wxId);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath))) {
            // 添加Excel到zip
            addFileToZip(zos, "chat_records.xlsx", new File(excelPath));
            task.setProgress(88);

            int totalImages = mediaMessages.size();
            int imageCount = 0;

            for (MsgVO msgVO : mediaMessages) {
                if (task.isCancelRequested()) {
                    break;
                }

                try {
                    String decodedPath = resolveAndDecodeImage(msgVO, wxId, basePath);
                    if (decodedPath != null) {
                        File decodedFile = new File(decodedPath);
                        if (decodedFile.exists()) {
                            String imgName = "images/" + decodedFile.getName();
                            addFileToZip(zos, imgName, decodedFile);
                        }
                    }
                } catch (Exception e) {
                    log.warn("导出图片失败, msgSvrId={}: {}", msgVO.getMsgSvrId(), e.getMessage());
                }

                imageCount++;
                if (totalImages > 0) {
                    task.setProgress(88 + (int) (10.0 * imageCount / totalImages));
                }
            }
        } catch (IOException e) {
            log.error("创建zip文件失败", e);
            return excelPath;
        }

        // 删除独立的Excel（已包含在zip中）
        FileUtil.del(excelPath);
        return zipPath;
    }

    /**
     * 解析并解码图片
     */
    private String resolveAndDecodeImage(MsgVO msgVO, String wxId, String basePath) {
        // 通过MD5查找图片
        if (StrUtil.isNotBlank(msgVO.getImgMd5())) {
            String imgRelPath = hardLinkImageAttributeRepository.queryHardLinkImage(
                    HexUtil.decodeHex(msgVO.getImgMd5()));
            if (StrUtil.isNotBlank(imgRelPath)) {
                String filePath = DirUtil.getDir(basePath, wxId, imgRelPath);
                if (FileUtil.exist(filePath)) {
                    String outDir = DirUtil.getImgDir(wxId);
                    FileUtil.mkdir(outDir);
                    return ImgDecoderUtil.decodeDat(filePath, outDir);
                }
            }
        }

        // 通过image路径查找
        if (StrUtil.isNotBlank(msgVO.getImage())) {
            String filePath = DirUtil.getDir(basePath, wxId, msgVO.getImage());
            if (FileUtil.exist(filePath)) {
                String outDir = DirUtil.getImgDir(wxId);
                FileUtil.mkdir(outDir);
                return ImgDecoderUtil.decodeDat(filePath, outDir);
            }
        }

        return null;
    }

    private void addFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file.toPath(), zos);
        zos.closeEntry();
    }

    /**
     * 获取对话人Id（使用捕获的wxId，不依赖currentUser）
     */
    private String getChatWxId(String talker, MsgVO msgVO, String wxId) {
        if (msgVO.getIsSender() == 1) {
            return wxId;
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
            log.error("解析对话人Id失败", e);
        }
        return talker;
    }

    /**
     * 获取群聊人数
     */
    private Integer handleMembersCount(byte[] roomData) {
        try {
            ChatRoomProto.ChatRoom chatRoom = ChatRoomProto.ChatRoom.parseFrom(roomData);
            return chatRoom.getMembersList().size();
        } catch (InvalidProtocolBufferException e) {
            log.error("解析RoomData失败", e);
        }
        return 0;
    }
}
