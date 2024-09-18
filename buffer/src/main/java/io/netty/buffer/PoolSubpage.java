/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import io.netty.util.SourceLogger;

final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;
    private final int memoryMapIdx;//表示在 memoryMap 数组中的某个节点位置，也就是二叉树中的位置
    private final int runOffset;//表示该 PoolSubpage 在 PoolChunk 中page数组的下标
    private final int pageSize;
    private final long[] bitmap;//用于管理子页面内存块的分配情况

    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    boolean doNotDestroy;
    int elemSize;//子页管理的内存单元大小，假设申请1024，那么该值为1024
    private int maxNumElems;
    private int bitmapLength;
    private int nextAvail;//用于跟踪下一个可分配内存块的位置。初始值为 0，表示从第一个位置开始分配
    private int numAvail;//表示可用的元素数量

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /**
     * Special constructor that creates a linked list head
     */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        //初始化bitmap，PoolSubpage管理的 内存块最小为 16B，所以先除以16
        //long 类型在 Java 中是 64 位，每 1 位可以用来表示一个内存段是否被使用，所以1 个 long 类型的变量可以表示 64 个内存段的使用情况。
        //那么总共需要的 long 类型变量的数量就是 pageSize / 16 / 64，这就是 bitmap 数组的大小。
        bitmap = new long[pageSize >>> 10];
        init(head, elemSize);
    }

    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;//分配单元的大小
        if (elemSize != 0) {
            //计算当前页面可以划分为多少个分配单元
            //maxNumElems：当前子页面可以分配的最大单元数量
            //numAvail：表示当前可用的内存单元数，初始时等于 maxNumElems，表示还没有任何内存块被分配。
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;// 用于跟踪下一个可分配内存块的位置。初始值为 0，表示从第一个位置开始分配
            bitmapLength = maxNumElems >>> 6;// 表示管理位图所需的数组长度,除以 64 从而计算需要多少个 long 类型的元素来存储位图
            if ((maxNumElems & 63) != 0) {//如果分配单元数不是 64 的倍数，那么还需要额外的一个 long 来存储剩余的位
                bitmapLength++;
            }

            for (int i = 0; i < bitmapLength; i++) {
                bitmap[i] = 0;//当前当前子页面内的所有内存单元都处于空闲状态。
            }
        }
        addToPool(head);//将当前子页面添加到子页面池（PoolSubpage 链表）中
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        final int bitmapIdx = getNextAvail();//获取下一个可用的索引 bitmapIdx
        int q = bitmapIdx >>> 6;//计算 bitmap 数组的索引 q,，相当于 bitmapIdx / 64，因为一个 long 类型可以表示 64 位
        int r = bitmapIdx & 63;//
        assert (bitmap[q] >>> r & 1) == 0;//断言 bitmap 的指定位置应该是 0
        bitmap[q] |= 1L << r;// bitmap 中的相应位置设置为 1，表示该内存块已经被分配。

        if (--numAvail == 0) {//减少可用的内存单元，如果为0，就从池中移除该 PoolSubpage
            removeFromPool();//注意这里只是从池中移除，还可以通过PoolChunk中的 PoolSubpage[]找到
        }

        return toHandle(bitmapIdx);
    }

    private static void printPoolSubpage(PoolSubpage<?> head){
        StringBuffer sb = new StringBuffer();
        PoolSubpage<?> s = head.next;
        while (s != head) {
            sb.append(s.elemSize + "->");
            s = s.next;
        }
        SourceLogger.info(PoolSubpage.class, "free subpage " + sb.toString());
    }
    /**
     * @return {@code true} if this subpage is in use.
     * {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        //bitmapIdx 是元素在位图中的索引，用来标识具体的内存块
        int q = bitmapIdx >>> 6; //确定位图中的数组索引，等价于 bitmapIdx / 64，因为每个 long 类型占 64 位。
        int r = bitmapIdx & 63; //bitmapIdx & 63 是位图中的具体位置，等价于 bitmapIdx % 64。
        assert (bitmap[q] >>> r & 1) != 0; //保当前位已分配
        bitmap[q] ^= 1L << r;//使用异或操作将对应位置的位清零（即释放该位的内存）。

        setNextAvail(bitmapIdx);//将当前的 bitmapIdx 设为下一个可用的索引

        //递增 numAvail，表示可用的元素数量增加
        if (numAvail++ == 0) {
            //如果 numAvail 之前为 0，意味着之前没有空闲的元素，现在有空闲元素了，因此需要将该 PoolSubpage 重新加入内存池
            printPoolSubpage(head);
            addToPool(head);
            printPoolSubpage(head);
            return true;
        }
        //检查 numAvail 是否等于 maxNumElems，即该 PoolSubpage 是否变得完全空闲。
        // 如果没有完全空闲（numAvail != maxNumElems），返回 true，表示不需要移除
        if (numAvail != maxNumElems) {
            return true;
        } else {
            // Subpage not in use (numAvail == maxNumElems)
            //如果完全空闲，但如果它是池中唯一的子页（即 prev == next），则不移除它，返回 true
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            //如果池中还有其他子页，且当前子页完全空闲，将其标记为可销毁
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j++) {
            if ((bits & 1) == 0) {
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }

    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    @Override
    public String toString() {
        final boolean doNotDestroy;
        final int maxNumElems;
        final int numAvail;
        final int elemSize;
        if (chunk == null) {
            // This is the head so there is no need to synchronize at all as these never change.
            doNotDestroy = true;
            maxNumElems = 0;
            numAvail = 0;
            elemSize = -1;
        } else {
            synchronized (chunk.arena) {
                if (!this.doNotDestroy) {
                    doNotDestroy = false;
                    // Not used for creating the String.
                    maxNumElems = numAvail = elemSize = -1;
                } else {
                    doNotDestroy = true;
                    maxNumElems = this.maxNumElems;
                    numAvail = this.numAvail;
                    elemSize = this.elemSize;
                }
            }
        }

        if (!doNotDestroy) {
            return "(" + memoryMapIdx + ": not in use)";
        }

        return "(" + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
                ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        synchronized (chunk.arena) {
            return maxNumElems;
        }
    }

    @Override
    public int numAvailable() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        synchronized (chunk.arena) {
            return numAvail;
        }
    }

    @Override
    public int elementSize() {
        if (chunk == null) {
            // It's the head.
            return -1;
        }

        synchronized (chunk.arena) {
            return elemSize;
        }
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }
}
