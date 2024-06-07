package mytest;

import io.netty.buffer.SizeClasses;
import io.netty.util.SourceLogger;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SizeClassesTest {

    private MockArena arena = new MockArena(8192, 13, 16777216, 0);

    @Test
    public void testSizeIdx2size() {

        for (int i = 100; i < 2048; i += 100) {
            int sizeIdx = arena.size2SizeIdx(i);
            int newSize = arena.sizeIdx2size(sizeIdx); //规格化之后的size
            System.out.println(i + "=>" + sizeIdx + "=>" + newSize);
        }
    }

    /**
     * 测试pages对应的pageIdx，用于定位runsAvail[pageIdx]，pages取值范围是1~2048
     */
    @Test
    public void testPages2pageIdxFloor() {
        Map<Integer, Range> map = new HashMap<>();
        for (int i = 1; i < 2049; ++i) {
            int pages = i;
            int pageIdx = arena.pages2pageIdxFloor(pages);
            System.out.println("pages " + pages + "=>pageIdx " + pageIdx);
            Range range = map.computeIfAbsent(pageIdx, (k) -> new Range());
            range.set(pages);
        }
        System.out.println("=========================");
        map.forEach((k, v) -> {
            System.out.println("pageIdx " + k + "=>pages " + v);
        });
    }

    /**
     * 测试请求的size到runSize的映射，runSize必须是8k的倍数
     */
    @Test
    public void testSizeIdx2RunSize() {
        int reqCapacity = 30 * 1024;
        for (int i = 0; i < 10; ++i) {
            int sizeIdx = arena.size2SizeIdx(reqCapacity);
            int ruleSize = arena.sizeIdx2size(sizeIdx);

            SourceLogger.info(this.getClass(),
                    "reqCapacity %sk -> sizeIdx %s -> ruleSize %sk",
                    reqCapacity / 1024,
                    sizeIdx,
                    ruleSize / 1024);

            reqCapacity += (1024 * 5);
        }
    }

    private static class Range {
        int min;
        int max;

        public void set(int pages) {
            if (min == 0) {
                min = pages;
            }
            if (max == 0) {
                max = pages;
            }
            min = Math.min(min, pages);
            max = Math.max(max, pages);
        }

        @Override
        public String toString() {
            if (min == max) {
                return String.valueOf(min);
            } else {
                return min + "~" + max;
            }
        }
    }


    private static class MockArena extends SizeClasses {

        protected MockArena(int pageSize, int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(pageSize, pageShifts, chunkSize, directMemoryCacheAlignment);
        }
    }
}
