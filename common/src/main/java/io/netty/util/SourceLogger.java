package io.netty.util;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class SourceLogger {

    private static final String SPLIT = " ";
    private static final String SPLIT1 = " - ";
    private static final String TAB = "  ";
    private static final int LOG_QUEUE_SIZE = 5000;
    private static final BlockingQueue<String> logQueue = new ArrayBlockingQueue<>(LOG_QUEUE_SIZE);
    private static final AtomicLong seq = new AtomicLong();
    private static final ThreadLocal<Context> localContext = new ThreadLocal<>();
    private static final Appender appender = new Appender();

    private static List<String> balckClassList = new ArrayList<>();
    private static List<String> balckPkgList = new ArrayList<>();

    private static boolean debug = false;//是否开启debug，默认不打开

    public static void setDebug(boolean debug) {
        SourceLogger.debug = debug;
    }

    private static Filter BLACK_LOGGER_NAME = (logger, log) -> {
        if (logger == null) {
            return false;
        }

        if (balckClassList.contains(logger.getSimpleName())) {
            return true;
        }

        for (String pkg : balckPkgList) {
            if (logger.getName().startsWith(pkg)) {
                return true;
            }
        }
        return false;
    };

    private static List<Filter> blackFilters = new ArrayList<>();

    static {
        //选举
        balckClassList.add("FollowerChecker");
        balckClassList.add("FollowersChecker");
        balckClassList.add("VotingConfiguration");
        balckClassList.add("VoteCollection");

        //shard
        balckClassList.add("IndicesClusterStateService");
        balckClassList.add("ClusterApplierService");

        balckPkgList.add("org.elasticsearch.cluster.coordination");
        balckPkgList.add("org.elasticsearch.discovery");

        try {
            blackFilters.add(BLACK_LOGGER_NAME);
            appender.init();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    interface Filter {
        boolean filter(Class<?> logger, String message);
    }

   

    private static void write(boolean block) throws Exception {
        List<String> parcel = new ArrayList<String>();
        if (block) {
            String message = logQueue.take();
            parcel.add(message);
        }
        logQueue.drainTo(parcel);
        for (String log : parcel) {
            appender.append(log + "\r\n");
        }
        appender.flush();
    }

    public synchronized static void info(String message, Object... args) {
        info(null, message, args);
    }


    private static void doWrite(Class<?> logger, String log) {

        for (Filter filter : blackFilters) {
            if (filter.filter(logger, log)) {
                return;
            }
        }

        //System.out.println(log);

    }

    public synchronized static void debug(Class<?> type, String message, Object... args) {
        if (!debug) {
            return;
        }
        String log = format(type, message, args);
        if (logQueue.size() < LOG_QUEUE_SIZE) {
            logQueue.add(log);
        }
    }

    public synchronized static void info(Class<?> type, String message, Object... args) {
        doWrite(type, format(type, message, args));
    }

    public synchronized static void error(Class<?> type, String message, Object... args) {
        doWrite(type, format(type, message, args));
    }

    public synchronized static void error(Class<?> type, String message, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        doWrite(type, format(type, message + "\r\nCause by: " + stackTrace));
    }

    public synchronized static void start(Class type, String message, Object... args) {
        Context context = localContext.get();
        if (context == null) {
            context = Context.create();
            localContext.set(context);
        }
        context.start();
        info(type, message, args);
    }

    public synchronized static void end(Class type, String message, Object... args) {
        Context context = localContext.get();
        info(type, message, args);
        if (context.end())
            localContext.remove();
    }


    private static String getCurrentMethod() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTraceElements) {
            System.out.println(element.toString());
        }

        return "";
    }

    private static String format(Class type, String message, Object... args) {
        StringBuilder sb = new StringBuilder();
//        sb.append(seq.getAndIncrement());
//        sb.append(SPLIT);
        //spanId
//        sb.append(formatSpanId());
//        sb.append(SPLIT);
        //sb.append(formatIndent());
        sb.append(formatDatetime());
        sb.append(SPLIT);
        sb.append(formatThread());
        sb.append(SPLIT);
        if (type != null) {
            sb.append(formatClass(type));
            sb.append(SPLIT1);
        }
        //sb.append(formatMDC());
        try {
            sb.append(MessageFormatter.arrayFormat(message, args));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private static String formatMDC() {
        if (MDC.get("role") != null) {
            return " [role=" + MDC.get("role") + "] ";
        } else {
            return "";
        }
    }

    private static String formatIndent() {
        StringBuilder sb = new StringBuilder();
        Context ctx = null;
        if ((ctx = localContext.get()) != null) {
            for (int i = 0; i < ctx.count.get(); ++i) {
                sb.append(TAB);
            }
        }
        return sb.toString();
    }

    private static String formatSpanId() {
        if (localContext.get() != null) {
            Context ctx = localContext.get();
            return ctx.traceId + "-" + ctx.count;
        } else {
            return "-";
        }
    }

    private static String formatDatetime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    private static String formatThread() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    private static String formatClass(Class type) {
        String simpleName = type.getSimpleName();
        return simpleName == null || simpleName.length() == 0 ? type.getName() : simpleName;
    }

    private static class Context {
        public String traceId;
        private AtomicInteger count = new AtomicInteger();

        public static Context create() {
            Context context = new Context();
            context.traceId = IdGenerator.generate();
            return context;
        }

        public void start() {
            count.incrementAndGet();
        }

        public boolean end() {
            return count.decrementAndGet() == 0;
        }
    }

    static class IdGenerator {
        private static final AtomicLong count = new AtomicLong();

        public static String generate() {
            long id = System.currentTimeMillis() + count.getAndIncrement();
            StringBuilder sb = new StringBuilder();
            sb.append(byteToString((byte) (id >>> 40 & 0xff)));
            sb.append(byteToString((byte) (id >>> 32 & 0xff)));
            sb.append(byteToString((byte) (id >>> 24 & 0xff)));
            sb.append(byteToString((byte) (id >>> 16 & 0xff)));
            sb.append(byteToString((byte) (id >>> 8 & 0xff)));
            sb.append(byteToString((byte) (id & 0xff)));
            return sb.toString();
        }

        private static String byteToString(byte b) {
            char[] chars = "0123456789abcdef".toCharArray();
            StringBuilder sb = new StringBuilder();
            sb.append(chars[(b & 0x0f0) >> 4]);
            sb.append(chars[b & 0x0f]);
            return sb.toString();
        }
    }

    static class MessageFormatter {

        public static String arrayFormat(String message, Object... args) {
            char[] chars = message.toCharArray();
            StringBuffer buffer = new StringBuffer();
            int ix = 0;
            int argsIdx = 0;
            while (ix < chars.length) {
                char current = chars[ix];
                if (current != '{') {
                    buffer.append(current);
                    ++ix;
                } else {
                    ++ix;
                    if (chars[ix] != '}') {
                        throw new IllegalArgumentException("expect:}, actually:" + chars[ix]);
                    }
                    if (argsIdx < args.length) {
                        buffer.append(args[argsIdx] == null ? "null" : args[argsIdx].toString());
                    } else {
                        buffer.append(" ");
                    }
                    ++argsIdx;
                    ++ix;
                }
            }
            return buffer.toString();
        }
    }

    public static class MDC {
        private static ThreadLocal<Map<String, String>> innerCtx = ThreadLocal.withInitial(() -> new HashMap<>());

        public static void put(String key, String value) {
            innerCtx.get().put(key, value);
        }

        public static String get(String key) {
            return innerCtx.get().get(key);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        //MDC.put("role", "client");
        while(true){
            SourceLogger.debug(SourceLogger.class, "test");
        }
    }

    static class Appender{
        private static final long MAX_FILE_SIZE = 1024*1024*100;
        private static final int MAX_RETAIN_COUNT = 5;
        private static final String LOG_FILE_NAME = "netty_source.log";
        private static final String LOG_FILE_PATTERN = "netty_source_%s.log";
        private static File logDir;
        private static File logFile;
        private static BufferedWriter writer;
        private AtomicLong byteSize = new AtomicLong();

        void init() throws FileNotFoundException {
            logDir= new File(System.getProperty("user.dir"), "logs");
            logDir.mkdirs();
            logFile = new File(logDir, LOG_FILE_NAME);
            System.out.println("logFile path: " + logFile);
    
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile)));
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        write(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setName("SourceLogger-Appender-Thread");
            t.setDaemon(true);
            t.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    write(false);
                    writer.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }

        public void append(String log) throws IOException{
            byteSize.addAndGet(log.getBytes().length);
            if (byteSize.get() >= MAX_FILE_SIZE) {
                rotateLogFile();
                byteSize.set(  0);
            }
            writer.write(log);
        }

    private void rotateLogFile() throws IOException {
        //关闭当前日志文件
        writer.close();
        
        //删除编号最大的文件
		final int maxBackupIndex = MAX_RETAIN_COUNT;
        File toDelete = new File(logDir,String.format(LOG_FILE_PATTERN,maxBackupIndex));
        if (toDelete.exists()) {
            toDelete.delete();
        }

        //依次从命名
        for (int i = maxBackupIndex-1; i > 0; i--) {
            File oldLogFile = new File(logDir,String.format(LOG_FILE_PATTERN,i));
            if (oldLogFile.exists()) {
                System.out.println("rename " + oldLogFile.getPath() + " to " + String.format(LOG_FILE_PATTERN,i+1));
                oldLogFile.renameTo(new File(logDir,String.format(LOG_FILE_PATTERN,i+1)));
            }
        }

        //重命名日志文件
        String newLogFileName = String.format(LOG_FILE_PATTERN,"1");
        System.out.println("rename " + logFile + " to " + newLogFileName);
        logFile.renameTo(new File(logDir,newLogFileName));

        //重新创建日志文件
        logFile = new File(logDir,LOG_FILE_NAME);
        writer = new BufferedWriter(new FileWriter(logFile, true));
    }

        public void flush() throws IOException{
            writer.flush();
        }
    }
}

