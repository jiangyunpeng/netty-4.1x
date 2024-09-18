package io.netty.buffer.metric;

import io.netty.buffer.PoolArenaMetric;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.toolkit.Action;
import io.netty.buffer.toolkit.FormatUtils;
import io.netty.buffer.toolkit.HttpEndpoint;
import io.netty.buffer.toolkit.TableBuilder;
import io.netty.util.SourceLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NettyMetric {

    private static final List<PooledByteBufAllocator> allocators = new ArrayList<>();

    static {
        HttpEndpoint.getInstance().registerAction(new AllocatorMetricAction("netty", "/netty/allocator"));
        HttpEndpoint.getInstance().registerAction(new LogAction("netty", "/netty/log"));
    }

    public static void registerAllocator(PooledByteBufAllocator allocator) {
        allocators.add(allocator);
    }

    private static String info() {
        TableBuilder tableBuilder = new TableBuilder();
        tableBuilder.setHeader(new String[]{
                "id",
                "numHeapArenas",
                "tinyCacheSize",
                "smallCacheSize",
                "normalCacheSize",
                "numThreadLocalCaches",
                "chunkSize",
                "usedHeapMemory",
                "realUsedMemory",
        });
        for (int i = 0; i < allocators.size(); ++i) {
            PooledByteBufAllocator allocator = allocators.get(i);
            if (allocator.numThreadLocalCaches() == 0) {
                continue;
            }
            tableBuilder.addRow(
                    String.valueOf(i),
                    String.valueOf(allocator.numHeapArenas()),
                    String.valueOf(allocator.tinyCacheSize()),//tiny缓存队列的大小
                    String.valueOf(allocator.smallCacheSize()),//small缓存队列的大小
                    String.valueOf(allocator.normalCacheSize()),//normal缓存队列的大小
                    String.valueOf(allocator.numThreadLocalCaches()),
                    FormatUtils.humanReadableByteSize(allocator.chunkSize()),
                    FormatUtils.humanReadableByteSize(allocator.usedHeapMemory()),
                    FormatUtils.humanReadableByteSize(allocator.realUsedHeapMemory())
            );

        }
        return tableBuilder.toString();
    }

    private static PooledByteBufAllocator getActive() {
        for (int i = 0; i < allocators.size(); ++i) {
            PooledByteBufAllocator allocator = allocators.get(i);
            if (allocator.numThreadLocalCaches() != 0) {
                return allocator;
            }
        }

        return null;
    }

    private static String detail() {
        PooledByteBufAllocator metric = getActive();
        List<PoolArenaMetric> arenaMetricList = metric.heapArenas();
        TableBuilder tableBuilder = new TableBuilder();
        tableBuilder.setHeader(new String[]{"arena", "tiny", "small", "normal", "allocate", "deAllocate", "total",
                "init", "q000", "q025", "q050", "q075", "q100"});

        for (int i = 0; i < arenaMetricList.size(); ++i) {
            PoolArenaMetric arenaMetric = arenaMetricList.get(i);
            tableBuilder.addRow(
                    "arena-" + i,
                    String.valueOf(arenaMetric.numActiveTinyAllocations()), //0， 512byte
                    String.valueOf(arenaMetric.numActiveSmallAllocations()), //（512byte， 8KB）
                    String.valueOf(arenaMetric.numActiveNormalAllocations()), // [8KB， 16M]
                    String.valueOf(arenaMetric.numAllocations()),
                    String.valueOf(arenaMetric.numDeallocations()),
                    FormatUtils.humanReadableByteSize(arenaMetric.numActiveBytes()),
                    arenaMetric.chunkLists().get(0).toString(), //qInit
                    arenaMetric.chunkLists().get(1).toString(), //q000
                    arenaMetric.chunkLists().get(2).toString(), //q025
                    arenaMetric.chunkLists().get(3).toString(), //q050
                    arenaMetric.chunkLists().get(4).toString(), //q075
                    arenaMetric.chunkLists().get(5).toString() //q100
            );
        }

        return tableBuilder.toString();
    }

    public static class LogAction extends Action {

        public LogAction(String artifact, String path) {
            super(artifact, path);
        }

        @Override
        public String execute(Map<String, String> params) throws IllegalArgumentException {
            String debug = params.get("debug");
            if(debug==null){
                return "require params eg: ?debug=false";
            }
            if (debug.equals("true")) {
                SourceLogger.setDebug(true);
            } else {
                SourceLogger.setDebug(false);
            }
            return "logger set " + debug;
        }
    }

    public static class AllocatorMetricAction extends Action {

        public AllocatorMetricAction(String artifact, String path) {
            super(artifact, path);
        }

        @Override
        public String execute(Map<String, String> params) throws IllegalArgumentException {
            String reset = params.get("reset");
            if (reset != null) {
                MetricRegistry.reset();
            }
            Meter cacheTiny = MetricRegistry.group("allocator").entry("cache").meter("tiny");
            Meter cacheSmall = MetricRegistry.group("allocator").entry("cache").meter("small");
            Meter cacheNormal = MetricRegistry.group("allocator").entry("cache").meter("normal");

            Meter arenaTiny = MetricRegistry.group("allocator").entry("arena").meter("tiny");
            Meter arenaSmall = MetricRegistry.group("allocator").entry("arena").meter("small");
            Meter arenaNormal = MetricRegistry.group("allocator").entry("arena").meter("normal");

            Meter poolTiny = MetricRegistry.group("allocator").entry("pool").meter("tiny");
            Meter poolSmall = MetricRegistry.group("allocator").entry("pool").meter("small");

            Meter reqBytes = MetricRegistry.group("allocator").entry("arena").meter("reqBytes");
            Meter reqCount = MetricRegistry.group("allocator").entry("arena").meter("reqCount");
            Meter normBytes = MetricRegistry.group("allocator").entry("arena").meter("normBytes");//norm只需要统计bytes

            Meter reqCount2 = MetricRegistry.group("allocator").entry("arena").meter("reqCount2");
            Meter reqBytes2 = MetricRegistry.group("allocator").entry("arena").meter("reqBytes2");//单独统计重新分配

            TableBuilder tableBuilder = new TableBuilder();
            tableBuilder.setHeader(new String[]{
                    "Key",
                    "cacheTiny",
                    "cacheSmall",
                    "cacheNormal",
                    "arenaTiny",
                    "arenaSmall",
                    "arenaNormal",
                    "poolTiny",
                    "poolSmall",
                    "reqBytes",
                    "reqCount",
                    "normalBytes",
                    "reAlloCount",
                    "reAlloBytes",
            });

            tableBuilder.addRow(
                    "Count:",
                    cacheTiny.getCount() + "-" + FormatUtils.percentX(cacheTiny.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    cacheSmall.getCount() + "-" + FormatUtils.percentX(cacheSmall.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    cacheNormal.getCount() + "-" + FormatUtils.percentX(cacheNormal.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    arenaTiny.getCount() + "-" + FormatUtils.percentX(arenaTiny.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    arenaSmall.getCount() + "-" + FormatUtils.percentX(arenaSmall.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    arenaNormal.getCount() + "-" + FormatUtils.percentX(arenaNormal.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    poolTiny.getCount() + "-" + FormatUtils.percentX(poolTiny.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    poolSmall.getCount() + "-" + FormatUtils.percentX(poolSmall.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    FormatUtils.humanReadableByteSize(reqBytes.getCount()),
                    String.valueOf(reqCount.getCount()),
                    FormatUtils.humanReadableByteSize(normBytes.getCount()),
                    reqCount2.getCount() + "-" + FormatUtils.percentX(reqCount2.getCount() / (reqCount.getCount() * 1.0f)) + "%",
                    FormatUtils.humanReadableByteSize(reqBytes2.getCount())//单独统计重新分配
            );
            tableBuilder.addRow(
                    "QPS:",
                    String.valueOf(FormatUtils.roundx1(cacheTiny.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(cacheSmall.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(cacheNormal.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(arenaTiny.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(arenaSmall.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(arenaNormal.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(poolTiny.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(poolSmall.getOneMinuteRate())),
                    FormatUtils.humanReadableByteSize(Math.round(reqBytes.getOneMinuteRate())),
                    String.valueOf(FormatUtils.roundx1(reqCount.getOneMinuteRate())),
                    FormatUtils.humanReadableByteSize(Math.round(normBytes.getOneMinuteRate())),
                    FormatUtils.roundx1(reqCount2.getOneMinuteRate()) + "",
                    FormatUtils.humanReadableByteSize(Math.round(reqCount2.getOneMinuteRate()))

            );

            return FormatUtils.toDateTimeString(MetricRegistry.startTime()) + "\n"
                    + info() + "\n"
                    + tableBuilder.toString() + "\n"
                    + detail();
        }
    }

}
