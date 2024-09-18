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

import io.netty.buffer.toolkit.FormatUtils;
import io.netty.util.SourceLogger;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 * <p>
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > chunk - a chunk is a collection of pages
 * > in this code chunkSize = 2^{maxOrder} * pageSize
 * <p>
 * To begin we allocate a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 * <p>
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 * <p>
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 * <p>
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 * <p>
 * depth=0        1 node (chunkSize)
 * depth=1        2 nodes (chunkSize/2)
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d)
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize)
 * <p>
 * depth=maxOrder is the last level and the leafs consist of pages
 * <p>
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 * <p>
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 * memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 * is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 * <p>
 * As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 * <p>
 * Initialization -
 * In the beginning we construct the memoryMap array by storing the depth of a node at each node
 * i.e., memoryMap[id] = depth_of_id
 * <p>
 * Observations:
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 * some of its children can still be allocated based on their availability
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 * is thus marked as unusable
 * <p>
 * Algorithm: [allocateNode(d) => we want to find the first node (from left) at height h that can be allocated]
 * ----------
 * 1) start at root (i.e., depth = 0 or id = 1)
 * 2) if memoryMap[1] > d => cannot be allocated from this chunk
 * 3) if left node value <= h; we can allocate from left subtree so move to left and repeat until found
 * 4) else try in right subtree
 * <p>
 * Algorithm: [allocateRun(size)]
 * ----------
 * 1) Compute d = log_2(chunkSize/size)
 * 2) Return allocateNode(d)
 * <p>
 * Algorithm: [allocateSubpage(size)]
 * ----------
 * 1) use allocateNode(maxOrder) to find an empty (i.e., unused) leaf (i.e., page)
 * 2) use this handle to construct the PoolSubpage object or if it already exists just call init(normCapacity)
 * note that this PoolSubpage object is added to subpagesPool in the PoolArena when we init() it
 * <p>
 * Note:
 * -----
 * In the implementation for improving cache coherence,
 * we store 2 pieces of information depth_of_id and x as two byte values in memoryMap and depthMap respectively
 * <p>
 * memoryMap[id]= depth_of_id  is defined above
 * depthMap[id]= x  indicates that the first node which is free to be allocated is at depth x (from root)
 */
final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;
    final int offset;
    private final byte[] memoryMap;//记录节点是否可申请状态，根节点从1开始，0是废弃的，value默认是对应的depth
    private final byte[] depthMap;//常量，记录每个节点对应初始depth，假设64k,即[0,0,1,1,2,2,2,2...]
    private final PoolSubpage<T>[] subpages;//保存分配的子页，数组长度等于page数量，通过1<<maxOrder计算
    /**
     * Used to determine if the requested capacity is equal to or greater than pageSize.
     */
    private final int subpageOverflowMask;
    private final int pageSize;
    private final int pageShifts;
    private final int maxOrder;
    private final int chunkSize;
    private final int log2ChunkSize;
    private final int maxSubpageAllocs;//可分配的page数量，等于1 << maxOrder，深度4的话，可分配的page数量为16
    /**
     * Used to mark memory as unusable
     */
    private final byte unusable;//不可用的标记maxOrder+1

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.
    private final Deque<ByteBuffer> cachedNioBuffers;

    int freeBytes;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize, int offset) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        this.offset = offset;
        unusable = (byte) (maxOrder + 1);
        log2ChunkSize = log2(chunkSize);
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex++;
            }
        }

        subpages = newSubpageArray(maxSubpageAllocs);//subpages数量等于chunk中的page数量
        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);
    }

    /**
     * Creates a special chunk that is not pooled.
     */
    PoolChunk(PoolArena<T> arena, T memory, int size, int offset) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        memoryMap = null;
        depthMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubpageAllocs = 0;
        cachedNioBuffers = null;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity, PoolThreadCache threadCache) {
        final long handle;
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize
            handle = allocateRun(normCapacity);//handle就是在二叉树中的索引，从根节点开始
            SourceLogger.info(this.getClass(), "allocate run from chunk normSize:{} handle:{}", normCapacity, handle);
        } else {
            handle = allocateSubpage(normCapacity);
            SourceLogger.info(this.getClass(), "allocate page from chunk normSize:{} ", normCapacity);
        }
        //如果小于0表示分配失败
        if (handle < 0) {
            return false;
        }
        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
        initBuf(buf, nioBuffer, handle, reqCapacity, threadCache);
        return true;
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {//1是根节点，根节点就不需要设置了
            int parentId = id >>> 1;//父节点
            byte val1 = value(id);//当前节点
            byte val2 = value(id ^ 1);//id^1切换到兄弟节点
            byte val = val1 < val2 ? val1 : val2;//取两者最小的值
            setValue(parentId, val);//设置parent的状态
            id = parentId;//递归
        }
    }

    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     *
     * @param id id
     */
    private void updateParentsFree(int id) {
        //它实现了在释放内存后，更新内存分配树中父节点的状态。
        // 具体来说，它是通过更新父节点的值来表示内存块是否可以被合并，从而重新形成更大的可用内存块
        int logChild = depth(id) + 1;//当前节点的深度加 1，
        while (id > 1) {//循环从当前节点开始，一直向上更新父节点，直到根节点
            int parentId = id >>> 1; //找到父节点的编号
            byte val1 = value(id); //当前节点 id 的值
            byte val2 = value(id ^ 1);  //当前节点相邻的兄弟节点的值
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up

            //这里是判断当前节点和兄弟节点是否可以合并
            if (val1 == logChild && val2 == logChild) {
                //表示两个子节点都处于相同的深度且未分配，因此可以合并为更大的内存块
                //父节点的值更新为 logChild - 1
                setValue(parentId, (byte) (logChild - 1));
            } else {
                //否则，将父节点的值更新为两个子节点中较小的那个值
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
    }

    /**
     * Algorithm to allocate an index in memoryMap when we query for a free node
     * at depth d
     * 从二叉树中递归找到我们合适的层
     *
     * @param d depth
     * @return index in memoryMap
     */
    private int allocateNode(int d) {
        int id = 1;//节点 ID，初始为 1，表示从树的根节点开始
        int initial = -(1 << d); // has last d bits = 0 and rest all = 1
        byte val = value(id);//表示当前节点的状态,比如是否可用，如果可用状态对应的depth，不可用状态对应的maxOrder+1
        if (val > d) { // 如果根节点已经被占用，直接退出
            return -1;
        }
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            id <<= 1;//1->2->4->8
            val = value(id);
            if (val > d) {
                id ^= 1;//切换到兄弟节点，类似+1
                val = value(id);//获取兄弟节点的值
            }
        }
        byte value = value(id);//再次检查分配节点状态
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);
        setValue(id, unusable); // 标记为不空用
        updateParentsAlloc(id);
        return id;
    }

    /**
     * Allocate a run of pages (>=1)
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateRun(int normCapacity) {
        //(log2(normCapacity) - pageShifts) 计算申请的容量节点到叶子节点的距离
        //pageShifts 表示单个页大小的对数，相当于log2(8k)=13
        //假设我们申请的是8k，那么就是0，假设是16k，那么就是1，假设是32k，那么就是2，64k就是3，以此类推
        int d = maxOrder - (log2(normCapacity) - pageShifts);//计算申请的normCapacity节点的深度
        int id = allocateNode(d);//表示在第d层分配，比如64kb是在4，depth从根节点向下，根节点为0,分别是[1024,512,256,128,64]
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);
        return id;
    }

    /**
     * Create / initialize a new PoolSubpage of normCapacity
     * Any PoolSubpage created / initialized here is added to subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    private long allocateSubpage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);//查找head，head在数组中保存
        int d = maxOrder; // subpages 只会分配一个page，所以是从maxOrder分配，因为maxOrder表示树的深度，最底部是叶子节点
        synchronized (head) {
            int id = allocateNode(d);//分配一个page，返回page在树中位置
            if (id < 0) {
                return id;
            }

            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;//分配一页减少8k

            int subpageIdx = subpageIdx(id);//通过id得到在subpages中的索引
            PoolSubpage<T> subpage = subpages[subpageIdx];
            if (subpage == null) {//分配page
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;
            } else {
                subpage.init(head, normCapacity);
            }
            return subpage.allocate();//在PoolSubpage中分配
        }
    }

    /**
     * Free a subpage or a run of pages
     * When a subpage is freed from PoolSubpage, it might be added back to subpage pool of the owning PoolArena
     * If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize, we can
     * completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     */
    void free(long handle, ByteBuffer nioBuffer) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);//通过bitmap记录subpage(子页)

        if (bitmapIdx != 0) { //如果不为空说明当前handle对应的是subpage,需要释放
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            PoolSubpage<T> head = arena.findSubpagePoolHead(subpage.elemSize);
            synchronized (head) {
                if (subpage.free(head, bitmapIdx & 0x3FFFFFFF)) {
                    return;
                }
            }
        }
        freeBytes += runLength(memoryMapIdx);//计算runLength
        setValue(memoryMapIdx, depth(memoryMapIdx));//还原节点状态
        updateParentsFree(memoryMapIdx);

        //这里的nioBuffer其实是PooledByteBuf中的tmpNioBuf，如果不为空会尝试缓存
        if (nioBuffer != null && cachedNioBuffers != null &&
                cachedNioBuffers.size() < PooledByteBufAllocator.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK) {
            cachedNioBuffers.offer(nioBuffer);
        }
    }

    void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity,
                 PoolThreadCache threadCache) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);
        if (bitmapIdx == 0) {
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            buf.init(this, nioBuffer, handle, runOffset(memoryMapIdx) + offset,
                    reqCapacity, runLength(memoryMapIdx), threadCache);
        } else {
            initBufWithSubpage(buf, nioBuffer, handle, bitmapIdx, reqCapacity, threadCache);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity,
                            PoolThreadCache threadCache) {
        initBufWithSubpage(buf, nioBuffer, handle, bitmapIdx(handle), reqCapacity, threadCache);
    }

    /***
     * 初始化PooledByteBuf，通过handle找到对应PoolSubpage，然后初始化 offset
     *
     * 该方法即可以首次被PoolChunk.allocate()中调用，也可以在MemoryRegionCache.allocate()中调用
     *
     */
    private void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer,
                                    long handle, int bitmapIdx, int reqCapacity, PoolThreadCache threadCache) {
        assert bitmapIdx != 0;

        //从之前 handle 中提取出 memoryMapIdx，memoryMapIdx 表示这个内存块在二叉树中的位置
        int memoryMapIdx = memoryMapIdx(handle);

        //根据 memoryMapIdx 计算出page的索引
        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;//断言subPage没有被销毁
        assert reqCapacity <= subpage.elemSize;//断言请求的数量小于等于subpage.elemSize

        buf.init(
                this, nioBuffer, handle,
                runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize + offset,
                reqCapacity, subpage.elemSize, threadCache);
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    private static int log2(int val) {
        // compute the (0-based, with lsb = 0) position of highest set bit i.e, log2
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);
    }

    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        int shift = id ^ 1 << depth(id);
        return shift * runLength(id);
    }

    private int subpageIdx(int memoryMapIdx) {
        //通过二叉树中的索引得到subPage的索引，原理是去掉索引的高位。
        //比如一个chunkSize为128kb的数组：[0,128,64,64,32,32,32,32,16,16,16,16,16,16,16,16,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8]，长度32
        //可以发现pageSize的个数16，刚好是数组长度的一半，页面数组的长度即16.
        //位置16二进制：10000^10000(maxSubpageAllocs也是16),两者结果0
        //位置28二进制：11100^10000,两者结果1100,结果是12
        //个人理解其实等同于 memoryMapIdx-maxSubpageAllocs
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }

    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        synchronized (arena) {
            return freeBytes;
        }
    }

    @Override
    public String toString() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }

        return new StringBuilder()
                .append("Chunk(")
                //.append(Integer.toHexString(System.identityHashCode(this)).substring(0, 2))
                .append(FormatUtils.humanReadableByteSize(chunkSize-freeBytes))
                .append(": ")
                .append(usage(freeBytes))
                .append("%")
//                .append(chunkSize - freeBytes)
//                .append('/')
//                .append(chunkSize)
                .append(')')
                .toString();
    }

    void destroy() {
        arena.destroyChunk(this);
    }
}
