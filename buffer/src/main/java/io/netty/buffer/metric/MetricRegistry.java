package io.netty.buffer.metric;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bairen
 * @description
 **/
public class MetricRegistry {

    private static Map<String, MetricGroup> metricGroupMap = new ConcurrentHashMap<>();
    private static long startTime = System.currentTimeMillis();

    public static long startTime() {
        return startTime;
    }

    public static void reset() {
        metricGroupMap.clear();
        startTime = System.currentTimeMillis();
    }

    public static MetricGroup group(String group) {
        return metricGroupMap.computeIfAbsent(group, (k) -> new MetricGroup(group));
    }

    /**
     * 表示不同的维度
     */
    public static class MetricGroup {
        private Map<String, MetricEntry> entryStatsMap = new ConcurrentHashMap<>();

        private String group;

        //限制MetricEntry的大小
        private int maxSize = -1;

        public MetricGroup(String group) {
            this.group = group;
        }

        /**
         * 返回一个监控项
         *
         * @param entry
         * @return
         */
        public MetricEntry entry(String entry) {
            if (maxSize > 0) {
                synchronized (this) {
                    if (entryStatsMap.size() >= maxSize) {
                        entryStatsMap.clear();
                    }
                }
            }
            return entryStatsMap.computeIfAbsent(entry, (k) -> new MetricEntry(entry));
        }

        /**
         * 返回所有监控项
         *
         * @return
         */
        public List<MetricEntry> entries() {
            return entries(null);
        }

        /**
         * 返回所有监控项并且排序
         *
         * @param comparator
         * @return
         */
        public List<MetricEntry> entries(Comparator<MetricEntry> comparator) {
            List<MetricEntry> list = new ArrayList<>(entryStatsMap.values());
            if (comparator != null)
                Collections.sort(list, comparator);
            return list;
        }

        /**
         * 清空所有监控项
         */
        public void clear() {
            entryStatsMap.clear();
        }

        /**
         * 限制group最大监控项
         *
         * @param maxSize
         * @return
         */
        public MetricGroup maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }
    }

    /**
     * 表示监控项
     */
    public static class MetricEntry {
        private String name;
        private Map<String, Object> fieldMap = new ConcurrentHashMap<>();
        private Map<String, Counter> counterMap = new ConcurrentHashMap<>();
        private Map<String, Meter> meterMap = new ConcurrentHashMap<>();

        public MetricEntry(String name) {
            this.name = name;
        }

        public Counter counter(String counter) {
            return counterMap.computeIfAbsent(counter, (k) -> new Counter());
        }

        public Meter meter(String meter) {
            return meterMap.computeIfAbsent(meter, (k) -> new Meter());
        }


        /**
         * 直接覆盖旧值
         *
         * @param key
         * @param value
         * @return
         */
        public MetricEntry field(String key, Object value) {
            fieldMap.put(key, value);
            return this;
        }
    }

    public static void main(String[] args) {
        MetricRegistry.group("consumer").entry("k2.tomcat.log").counter("msgCount").inc();
        MetricRegistry.group("consumer").entry("k2.tomcat.log").counter("msgTraffic").inc();
        MetricRegistry.group("consumer").entry("k2.tomcat.log").meter("msgTraffic").mark();
    }

}
