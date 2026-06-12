package com.xcs.wx.repository;

import com.xcs.wx.domain.Msg;
import com.xcs.wx.domain.vo.CountRecentMsgsVO;
import com.xcs.wx.domain.vo.MsgTypeDistributionVO;
import com.xcs.wx.domain.vo.TopContactsVO;

import java.util.List;

/**
 * 消息 Repository
 *
 * @author xcs
 * @date 2023年12月25日15:31:37
 */
public interface MsgRepository {

    /**
     * 根据talker与分页信息查询聊天记录
     *
     * @param talker       对话着
     * @param nextSequence 下一个序列号
     * @return Msg
     */
    List<Msg> queryMsgByTalker(String talker, Long nextSequence);

    /**
     * 导出数据
     *
     * @param talker 对话着
     * @return Msg
     */
    List<Msg> exportMsg(String talker);

    /**
     * 微信消息类型及其分布统计
     *
     * @return MsgTypeDistributionVO
     */
    List<MsgTypeDistributionVO> msgTypeDistribution();

    /**
     * 统计过去 15 天每天的发送和接收消息数量
     *
     * @return MsgTrendVO
     */
    List<CountRecentMsgsVO> countRecentMsgs();

    /**
     * 最近一个月内微信互动最频繁的前10位联系人
     *
     * @return MsgRankVO
     */
    List<TopContactsVO> topContacts();

    /**
     * 统计发送消息数量
     *
     * @return 消息数量
     */
    int countSent();

    /**
     * 统计接受消息数量
     *
     * @return 消息数量
     */
    int countReceived();

    /**
     * 按指定wxId统计某talker的消息总数（支持时间范围过滤）
     *
     * @param wxId      微信账号
     * @param talker    聊天对象
     * @param startTime 开始时间戳(秒)，可为null
     * @param endTime   结束时间戳(秒)，可为null
     * @return 消息总数
     */
    int countMsgByTalker(String wxId, String talker, Long startTime, Long endTime);

    /**
     * 按指定wxId分批读取消息（sequence游标分页，支持时间范围过滤）
     *
     * @param wxId        微信账号
     * @param talker      聊天对象
     * @param maxSequence 当前游标（不含），首次传Long.MAX_VALUE
     * @param batchSize   每批大小
     * @param startTime   开始时间戳(秒)，可为null
     * @param endTime     结束时间戳(秒)，可为null
     * @return 消息列表
     */
    List<Msg> queryMsgBatch(String wxId, String talker, Long maxSequence, int batchSize,
                            Long startTime, Long endTime);
}
