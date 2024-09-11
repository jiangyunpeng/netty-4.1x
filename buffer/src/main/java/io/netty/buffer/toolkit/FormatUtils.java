/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package io.netty.buffer.toolkit;


import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * @author bairen
 */
public final class FormatUtils {

    /*
     * yyyy-MM-dd HH:mm:ss
     */
    private static final ThreadLocal<SimpleDateFormat> dateTimeFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseDateTime(String dateTimeString) throws IllegalArgumentException {
        try {
            return dateTimeFormatter.get().parse(dateTimeString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：yyyy-MM-dd HH:mm:ss", e);
        }
    }

    public static String toDateTimeString(long millis) throws IllegalArgumentException {
        return dateTimeFormatter.get().format(new Date(millis));
    }


    public static String toDateTimeString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return dateTimeFormatter.get().format(date);
    }


    private static final ThreadLocal<SimpleDateFormat> dateMinuteFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseDateMinute(String dateTimeString) throws IllegalArgumentException {
        try {
            return dateMinuteFormatter.get().parse(dateTimeString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：yyyy-MM-dd HH:mm", e);
        }
    }

    public static String toDateMinuteString(long millis) throws IllegalArgumentException {
        return dateMinuteFormatter.get().format(new Date(millis));
    }


    public static String toDateMinuteString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return dateMinuteFormatter.get().format(date);
    }

    /*
     * yyyy-MM-dd_HH
     */
    private static final ThreadLocal<SimpleDateFormat> dateHourFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd_HH");
        }
    };


    public static Date parseDateHour(String dateStr) {
        Date date;
        try {
            date = dateHourFormat.get().parse(dateStr);
        } catch (ParseException e) {
            throw new IllegalArgumentException("非法时间：" + dateStr + "，正确的格式为 yyyy-MM-dd_HH", e);
        }
        return date;
    }


    public static String toDateHourString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return dateHourFormat.get().format(date);
    }

    /*
     * yyyy-MM-dd HH:mm:ss.SSS
     */
    private static final ThreadLocal<SimpleDateFormat> dateTimeMillisFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseDateTimeMillis(String dateTimeString) throws IllegalArgumentException {
        try {
            return dateTimeMillisFormatter.get().parse(dateTimeString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：yyyy-MM-dd HH:mm:ss.SSS", e);
        }
    }

    public static String toDateTimeMillisString(long millis) throws IllegalArgumentException {
        return dateTimeMillisFormatter.get().format(new Date(millis));
    }


    public static String toDateTimeMillisString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return dateTimeMillisFormatter.get().format(date);
    }


    private static final ThreadLocal<SimpleDateFormat> dateFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseDate(String dateString) throws IllegalArgumentException {
        try {
            return dateFormatter.get().parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：yyyy-MM-dd", e);
        }

    }

    public static String toDateString(long millis) throws IllegalArgumentException {
        return dateFormatter.get().format(new Date(millis));
    }


    public static String toDateString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return dateFormatter.get().format(date);
    }

    private static final String DATE_FORMAT = "yyyy/MM/dd HH:mm:ss";
    private static final ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
            format.setLenient(false);
            return format;
        }
    };

    public static Date parseTimeRangeDate(String dateStr) {
        Date date;
        try {
            date = formatter.get().parse(dateStr);
        } catch (ParseException e) {
            throw new IllegalArgumentException("非法时间：" + dateStr + "，正确的格式为 " + DATE_FORMAT);
        }
        return date;
    }

    public static String formatTimeRange(Date date) {
        return formatter.get().format(date);
    }

    /*
     * yyyy/MM/dd
     */
    private static final ThreadLocal<SimpleDateFormat> pageDateFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
            format.setLenient(false);
            return format;
        }
    };

    public static String toPageDateString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return pageDateFormatter.get().format(date);
    }

    public static String toPageDateString(long time) throws IllegalArgumentException {
        return pageDateFormatter.get().format(time);
    }

    public static Date parsePageDate(String dateString) throws IllegalArgumentException {
        try {
            return pageDateFormatter.get().parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：yyyy/MM/dd", e);
        }
    }

    /*
     * yyyyMMdd
     */
    private static final ThreadLocal<SimpleDateFormat> yyyyMMddFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseYyyyMMdd(String dateString) throws IllegalArgumentException {
        try {
            return yyyyMMddFormatter.get().parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：yyyyMMdd", e);
        }
    }

    public static String toYyyyMMddString(long millis) throws IllegalArgumentException {
        return yyyyMMddFormatter.get().format(new Date(millis));
    }

    public static String toYyyyMMddString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return yyyyMMddFormatter.get().format(date);
    }

    /*
     * gmtDate: EEE, dd MMM yyyy HH:mm:ss zzz
     */
    private static final ThreadLocal<SimpleDateFormat> gmtDateFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            format.setLenient(false);
            return format;
        }
    };

    public static Date parseGmtDate(String dateString) throws IllegalArgumentException {
        try {
            return gmtDateFormatter.get().parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：EEE, dd MMM yyyy HH:mm:ss zzz", e);
        }
    }

    public static String toGmtDateString(long millis) throws IllegalArgumentException {
        return gmtDateFormatter.get().format(new Date(millis));
    }

    /*
     * HH:mm:ss
     */
    private static final ThreadLocal<SimpleDateFormat> secondTimeFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseSecondTime(String dateString) throws IllegalArgumentException {
        try {
            return secondTimeFormatter.get().parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：HH:mm:ss", e);
        }
    }

    public static String toSecondTimeString(long millis) throws IllegalArgumentException {
        return secondTimeFormatter.get().format(new Date(millis));
    }

    /*
     * HH:mm
     */
    private static final ThreadLocal<SimpleDateFormat> minuteTimeFormatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm");
            format.setLenient(false);
            return format;
        }
    };


    public static Date parseMinuteTime(String dateString) throws IllegalArgumentException {
        try {
            return minuteTimeFormatter.get().parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("日期格式不正确，正确格式为：HH:mm", e);
        }
    }

    public static String toMinuteTimeString(long millis) throws IllegalArgumentException {
        return minuteTimeFormatter.get().format(new Date(millis));
    }


    public static String toGmtDateString(Date date) throws IllegalArgumentException {
        if (date == null) {
            return null;
        }
        return gmtDateFormatter.get().format(date);
    }


    public static double round(double value, int decimals) {
        double d = Math.pow(10, decimals);
        return Math.round(value * d) / d;
    }


    public static long roundx0(double value) {
        return Math.round(value);
    }


    public static double roundx1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * 四舍五入到小数点后两位
     *
     *value

     */
    public static double roundx2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /**
     * 四舍五入到小数点后三位
     *
     *value

     */
    public static double roundx3(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static final ThreadLocal<DecimalFormat> roundx2Formatter = new ThreadLocal<DecimalFormat>() {
        @Override
        protected DecimalFormat initialValue() {
            DecimalFormat format = new DecimalFormat("0.00");
            return format;
        }
    };

    private static final ThreadLocal<DecimalFormat> roundx4Formatter = new ThreadLocal<DecimalFormat>() {
        @Override
        protected DecimalFormat initialValue() {
            DecimalFormat format = new DecimalFormat("0.0000");
            return format;
        }
    };

    /**
     * 四舍五入到小数点后四位，返回字符串，因为返回 double toString 时会转成科学计数法
     *
     *value

     */
    public static String roundx4(double value) {
        return roundx4Formatter.get().format(value);
    }

    private static final long KB_SIZE = 1024;
    private static final long MB_SIZE = 1024 * KB_SIZE;
    private static final long GB_SIZE = 1024 * MB_SIZE;
    private static final long TB_SIZE = 1024 * GB_SIZE;
    private static final long PB_SIZE = 1024 * TB_SIZE;

    /**
     * 字符串样式格式化
     *
     *bytes

     */
    public static String humanReadableByteSize(String bytes) {
        try {
            Long lbytes = Long.valueOf(bytes);
            return humanReadableByteSize(lbytes);
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 将字节表示成恰当的其他表示法。
     *
     *bytes

     */
    public static String humanReadableByteSize(long bytes) {
        boolean negative = bytes < 0;
        long fix = (bytes == Long.MIN_VALUE) ? Long.MAX_VALUE : Math.abs(bytes);
        String result;
        if (fix >= PB_SIZE) {
            double pb = fix / (double) PB_SIZE;
            result = roundx1(pb) + "PB";
        } else if (fix >= TB_SIZE) {
            double tb = fix / (double) TB_SIZE;
            result = roundx1(tb) + "TB";
        } else if (fix >= GB_SIZE) {
            double gb = fix / (double) GB_SIZE;
            result = roundx1(gb) + "GB";
        } else if (fix >= MB_SIZE) {
            double mb = fix / (double) MB_SIZE;
            result = roundx1(mb) + "MB";
        } else if (fix >= KB_SIZE) {
            double kb = fix / (double) KB_SIZE;
            result = roundx1(kb) + "KB";
        } else {
            result = fix + "B";
        }
        return negative ? ("-" + result) : result;
    }

    private static final long[] TIME_SPANS_MILLIS = { TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(1), //
        TimeUnit.MINUTES.toMillis(1), TimeUnit.SECONDS.toMillis(1), //
        1 };

    private static final String[] TIME_SPANS_TEXT = { "d", "h", "m", "s", "ms" };

    private static final String[] TIME_SPANS_CHINESE_TEXT = { "\u5929", "\u5C0F\u65F6", "\u5206\u949F", "\u79D2",
        "\u6BEB\u79D2" };

    /**
     * 字符串样式格式化
     *
     *millis

     */
    public static String humanReadableTimeSpan(String millis) {
        try {
            Long lmillis = Long.valueOf(millis);
            return humanReadableTimeSpan(lmillis);
        } catch (NumberFormatException e) {
            return "未知";
        }
    }

    /**
     * 将时间表示成适合阅读的表示法。为简明起见，只显示最大的两个时间单位。
     *
     *millis

     */
    public static String humanReadableTimeSpan(long millis) {
        return humanReadableTimeSpan(millis, 2, TIME_SPANS_TEXT);
    }

    /**
     * 字符串样式格式化
     *
     *millis

     */
    public static String humanReadableChineseTimeSpan(String millis) {
        try {
            Long lmillis = Long.valueOf(millis);
            return humanReadableChineseTimeSpan(lmillis);
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 将时间表示成适合阅读的中文表示法。为简明起见，只显示最大的两个时间单位。
     *
     *millis

     */
    public static String humanReadableChineseTimeSpan(long millis) {
        return humanReadableTimeSpan(millis, 2, TIME_SPANS_CHINESE_TEXT);
    }

    /**
     * 将时间表示成适合阅读的表示法。为简明起见，只显示最大的 maxTimeUnit 个时间单位。
     *
     *millis
     *maxTimeUnit
     *textLabels

     */
    private static String humanReadableTimeSpan(long millis, int maxTimeUnit, String[] textLabels) {
        boolean negative = millis < 0;
        long fix = (millis == Long.MIN_VALUE) ? Long.MAX_VALUE : Math.abs(millis);
        int appendUnit = 0;
        StringBuilder appender = new StringBuilder();
        if (negative) {
            appender.append('-');
        }
        final int l = TIME_SPANS_MILLIS.length;
        for (int i = 0; i < l && fix > 0; ++i) {
            final long span = TIME_SPANS_MILLIS[i];
            if (fix >= span) {
                long unit = fix / span;
                fix %= span;
                appender.append(unit).append(textLabels[i]);
                if (++appendUnit >= maxTimeUnit) {
                    return appender.toString();
                }
            }
        }
        if (appender.length() == 0) {
            appender.append(fix).append(textLabels[l - 1]);
        }
        return appender.toString();
    }

    private static final long A_HUNDRED_MILLION = 100000000; // 亿
    private static final long TEN_THOUSAND = 10000; // 万

    public static String humanReadableFormat(String obj) {
        String result = null;
        try {
            result = humanReadableNumber(Long.parseLong(obj));
        } catch (NumberFormatException e) {
            result = humanReadablePercent(Double.parseDouble(obj));
        }
        return result;
    }

    public static String humanReadableFormat(long obj) {
        return humanReadableNumber(obj);
    }

    public static String humanReadableFormat(double obj) {
        return humanReadablePercent(obj);
    }

    /**
     * 字符串样式格式化
     *
     *number

     */
    public static String humanReadableNumber(String number) {
        try {
            Long lnumber = Long.valueOf(number);
            return humanReadableNumber(lnumber);
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 将数字表示成适合阅读的表示法。为简明起见，只显示最大的 maxUnit 个单位。
     *
     *number

     */
    public static String humanReadableNumber(long number) {
        return humanReadableNumber(number, 2);
    }

    public static String humanReadablePercent(String number) {
        try {
            Double dnumber = Double.valueOf(number);
            return humanReadablePercent(dnumber);
        } catch (Exception e) {
            return "未知";
        }
    }

    /*
     * 将数字标识成百分比
     */
    public static String humanReadablePercent(double number) {
        return String.format("%.2f", number * 100) + "%";
    }

    /**
     * 字符串样式格式化
     *
     *number

     */
    public static String humanReadableLargeNumber(String number) {
        try {
            Double dnumber = Double.valueOf(number);
            return humanReadableLargeNumber(dnumber);
        } catch (Exception e) {
            return "未知";
        }
    }

    public static String humanReadableLargeNumber(double number) {
        if (number >= 10000) {
            return humanReadableNumber((long) number);
        } else {
            return String.valueOf(roundx2(number));
        }
    }

    /**
     * 将数字表示成适合阅读的表示法。为简明起见，只显示最大的两个单位。
     *
     *number
     *maxUnit

     */
    public static String humanReadableNumber(long number, int maxUnit) {
        boolean negative = number < 0;
        long fix = (number == Long.MIN_VALUE) ? Long.MAX_VALUE : Math.abs(number);
        StringBuilder appender = new StringBuilder();
        if (negative) {
            appender.append('-');
        }
        int appendUnit = 0;
        if (fix >= A_HUNDRED_MILLION) {
            long x = fix / A_HUNDRED_MILLION;
            fix %= A_HUNDRED_MILLION;
            appender.append(x).append('\u4ebf');
            ++appendUnit;
        }
        if (fix >= TEN_THOUSAND) {
            long x = fix / TEN_THOUSAND;
            fix %= TEN_THOUSAND;
            appender.append(x).append('\u4e07');
            if (++appendUnit >= maxUnit) {
                return appender.toString();
            }
        }
        if (appendUnit < maxUnit && (fix > 0 || appender.length() == 0)) {
            appender.append(fix);
        }
        return appender.toString();
    }

    /**
     * 字符串超过指定长度，将被截断到 length 长，末尾追加省略号
     *
     *str
     *length

     */
    public static final String truncateWithEllipsis(String str, int length) {
        if (str == null || str.length() < length || length < 4) {
            return str;
        }
        return str.substring(0, length - 3) + "...";
    }

    public static final String diffTime(long num, long base) {
        long diff = num - base;
        return diff == 0 ? "+0ms" : (diff > 0 ? ("+" + humanReadableTimeSpan(diff)) : humanReadableTimeSpan(diff));
    }

    public static final String percent(double dividend, double divisor) {
        return divisor != 0.0 ? percent(dividend / divisor) : (dividend != 0.0 ? "-" : "0.00%");
    }

    public static final String percent(double value) {
        if (value < 0.00005 && value > -0.00005) {
            return "0.00%";
        }
        return roundx2(value * 100) + "%";
    }

    public static final String percentX(double dividend, double divisor) {
        return divisor != 0.0 ? percentX(dividend / divisor) : (dividend != 0.0 ? "-" : "0.00");
    }

    public static final String percentX(double value) {
        if (value > 1) {
            return String.valueOf(roundx2Formatter.get().format(value));
        }
        if (value < 0.00005 && value > -0.00005) {
            return "0.00";
        }
        return String.valueOf(roundx2Formatter.get().format(value * 100));
    }

    public static final String percentx0(double dividend, double divisor) {
        return divisor != 0.0 ? percentx0(dividend / divisor) : (dividend != 0.0 ? "-" : "0%");
    }

    public static final String percentx0(double value) {
        if (value < 0.005 && value > -0.005) {
            return "0%";
        }
        return roundx0(value * 100) + "%";
    }

    public static final String percentx1(double dividend, double divisor) {
        return divisor != 0.0 ? percentx1(dividend / divisor) : (dividend != 0.0 ? "-" : "0.0%");
    }

    public static final String percentx1(double value) {
        if (value < 0.0005 && value > -0.0005) {
            return "0.0%";
        }
        return roundx1(value * 100) + "%";
    }

    public static final String escapeToUnicodeString(char c) {
        return "\\u" + Integer.toHexString(c | 0x10000).substring(1);
    }

    public static final String getIdentityCode(Object obj) {
        if (obj == null) {
            return "(null)";
        } else {
            return "(0x" + Integer.toHexString(System.identityHashCode(obj)) + ")";
        }
    }

    /**
     * 在 CamelCase 风格的字符串的大写字符前加上隐形空格（让浏览器自动换行），隐形空格之间的间隔至少为 step 个字符
     *
     *appender
     *            输出
     *str
     *            待处理的字符串
     *start
     *            开始位置
     *end
     *            结束位置
     *step
     *            间隔
     */
    public static final void appendWithSoftLineBreak(StringBuilder appender, String str, int start, int end, int step) {
        int i = start;
        int inc = 0;
        while (i < end) {
            char c = str.charAt(i);
            if (inc < step) {
                ++inc;
            } else if (inc == step && c >= 'A' && c <= 'Z') {
                appender.append("&#8203;");
                inc = 0;
            }
            appender.append(c);
            ++i;
        }
    }

    public static final float abs(float a) {
        return Math.abs(a);
    }

    public static final String getExceptionStackTrace(Exception e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);
        e.printStackTrace(pw);
        pw.close();
        return new String(out.toByteArray());

    }

}
