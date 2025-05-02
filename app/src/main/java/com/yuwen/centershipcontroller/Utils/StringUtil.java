package com.yuwen.centershipcontroller.Utils;

import java.util.Arrays;
import java.util.List;

/**
 * @author yuwen
 */
public class StringUtil {

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 字符串拼接
     */
    public static String join(String delimiter, String... elements) {
        return String.join(delimiter, elements);
    }

    /**
     * 字符串截取
     */
    public static String subString(String str, int start, int end) {
        if (str == null) return "";
        int len = str.length();
        if (start < 0) start = 0;
        if (end > len) end = len;
        return str.substring(start, end);
    }

    /**
     * 字符串替换
     */
    public static String replace(String source, String oldChar, String newChar) {
        if (source == null) return "";
        return source.replace(oldChar, newChar);
    }

    /**
     * 转换为小写
     */
    public static String toLowerCase(String str) {
        if (str == null) return "";
        return str.toLowerCase();
    }

    /**
     * 转换为大写
     */
    public static String toUpperCase(String str) {
        if (str == null) return "";
        return str.toUpperCase();
    }

    /**
     * 去除两端空格
     */
    public static String trim(String str) {
        if (str == null) return "";
        return str.trim();
    }

    /**
     * 按分隔符分割字符串
     */
    public static List<String> splitToList(String str, String delimiter) {
        if (isEmpty(str)) return Arrays.asList();
        return Arrays.asList(str.split(delimiter));
    }

    /**
     * 格式化字符串
     */
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }

    /**
     * 字符串比较
     */
    public static boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * 忽略大小写比较
     */
    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    /**
     * 检查是否包含子串
     */
    public static boolean contains(String str, String sub) {
        if (isEmpty(str) || isEmpty(sub)) return false;
        return str.contains(sub);
    }
}