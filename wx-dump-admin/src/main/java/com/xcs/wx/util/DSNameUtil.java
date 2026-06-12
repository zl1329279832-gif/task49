package com.xcs.wx.util;

import cn.hutool.extra.spring.SpringUtil;
import com.xcs.wx.service.UserService;

/**
 * DSNameUtil
 *
 * @author 林雷
 * @date 2024年6月27日17:26:43
 */
public class DSNameUtil {

    /**
     * 异步线程中覆盖当前用户wxId（避免依赖SwitchUser.config）
     */
    private static final ThreadLocal<String> OVERRIDE_WX_ID = new ThreadLocal<>();

    private DSNameUtil() {
    }

    /**
     * 设置当前线程的wxId覆盖值（用于异步导出等场景）
     *
     * @param wxId wxId
     */
    public static void setOverrideWxId(String wxId) {
        OVERRIDE_WX_ID.set(wxId);
    }

    /**
     * 清除当前线程的wxId覆盖值
     */
    public static void clearOverrideWxId() {
        OVERRIDE_WX_ID.remove();
    }

    /**
     * 获取数据源名称
     *
     * @param dbName 数据库名
     * @return dsName
     */
    public static String getDSName(String dbName) {
        String wxId = OVERRIDE_WX_ID.get();
        if (wxId == null) {
            wxId = SpringUtil.getBean(UserService.class).currentUser();
        }
        return getDSName(wxId, dbName);
    }

    /**
     * 获取数据源名称
     *
     * @param wxId   wxId
     * @param dbName 数据库名
     * @return dsName
     */
    public static String getDSName(String wxId, String dbName) {
        return wxId + "#" + dbName;
    }
}
