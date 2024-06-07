package io.netty.util;

import java.io.BufferedWriter;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

public class SourceLogger {
    private static final String SPLIT = " ";
    private static final String SPLIT1 = " - ";
    private static final String TAB = "  ";
    private static final BlockingQueue<String> logQueue = new LinkedBlockingDeque<>();
    private static final AtomicLong seq = new AtomicLong();
    private static File logFile;
    private static BufferedWriter writer;

    public synchronized static void info(Class type, String message, Object... args) {
        write(format(type, message, args));
    }

    private static void write(String log) {
        System.out.println(log);
    }

    private static String format(Class type, String message, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatDatetime());
        sb.append(SPLIT);
        sb.append(formatThread());
        sb.append(SPLIT);
        if (type != null) {
            sb.append(formatClass(type));
            sb.append(SPLIT1);
        }
        sb.append(String.format(message,args));
        return sb.toString();
    }

    private static String formatDatetime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    private static String formatThread() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    private static String formatClass(Class type) {
        return type.getSimpleName();
    }
}
