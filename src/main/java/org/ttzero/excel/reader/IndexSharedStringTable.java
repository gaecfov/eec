/*
 * Copyright (c) 2017-2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ttzero.excel.reader;

import org.ttzero.excel.entity.ExcelWriteException;
import org.ttzero.excel.entity.SharedStringTable;
import org.ttzero.excel.util.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author guanquan.wang at 2019-05-10 20:06
 */
public class IndexSharedStringTable extends SharedStringTable {
    /**
     * The index temp path
     */
    private final Path temp;

    private final SeekableByteChannel channel;

    /**
     * Byte array buffer
     */
    private ByteBuffer buffer, readBuffer;

    /**
     * The short sector size
     */
    private int ssst = 6;

    /**
     * Setting how many records to split
     * Default 64
     */
    private int kSplit = 0x7FFFFFFF >> ssst << ssst;

    /**
     * A multiplexing byte array
     */
    private byte[] bytes;

    /**
     * A fix length multiplexing char array
     */
    private final char[] chars = new char[1];

    /**
     * Cache the getting index
     */
    private int index = -1;

    /**
     * Current read/write status
     */
    private byte status;

    private static final byte READ = 1, WRITE = 0;

    /**
     * Create a temp file to storage the index
     *
     * @throws IOException if I/O error occur.
     */
    public IndexSharedStringTable() throws IOException {
        super();

        Path superPath = getTemp();
        temp = Files.createFile(Paths.get(superPath.toString() + ".idx"));
        channel = Files.newByteChannel(temp, StandardOpenOption.WRITE, StandardOpenOption.READ);
        buffer = ByteBuffer.allocate(1 << 11);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        readBuffer = ByteBuffer.allocate(1 << 12);
        readBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Constructor a IndexSharedStringTable with a exists index file
     *
     * @param path the index file path
     * @throws IOException if file not exists or I/O error occur.
     */
    public IndexSharedStringTable(Path path) throws IOException {
        super(Paths.get(path.toString().substring(0, path.toString().length() - 4)));
        temp = path;
        channel = Files.newByteChannel(temp, StandardOpenOption.WRITE, StandardOpenOption.READ);
        channel.position(channel.size());
        buffer = ByteBuffer.allocate(1 << 11);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        readBuffer = ByteBuffer.allocate(1 << 12);
        readBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Size of a short-sector in the index stream in power-of-two (sssz),
     * real short-sector size is short_sec_size = 2<sup>sssz</sup> bytes
     * (maximum value is sector size ssz,
     *
     * @param sssz the short-sector size
     */
    public void setShortSectorSize(int sssz) {
        if (sssz < 1) {
            throw new IllegalArgumentException("The short sector size must large than 1.");
        }
        if (sssz > 20) {
            throw new IllegalArgumentException("The short sector size must less than 20.");
        }
        this.ssst = sssz;
        // reset split
        this.kSplit = 0x7FFFFFFF >> sssz << sssz;
    }

    /**
     * Write character value into table
     *
     * @param c the character value
     * @return the value index of table
     * @throws IOException if I/O error occur
     */
    @Override
    public int push(char c) throws IOException {
        putsIndex();
        return super.push(c);
    }

    /**
     * Write string value into table
     *
     * @param key the string value
     * @return the value index of table
     * @throws IOException if I/O error occur
     */
    @Override
    public int push(String key) throws IOException {
        putsIndex();
        return super.push(key);
    }

    /**
     * Getting by index
     *
     * @param index the value's index in table
     * @return the string value at index
     * @throws IOException if I/O error occur
     */
    public String get(int index) throws IOException {
        checkBound(index);
        boolean write;
        if ((write = status == WRITE) || index != this.index) {
            if (write) {
                super.mark();
                status = READ;
            }
            long position = getIndexPosition(index);
            readBuffer.clear();

            super.skip(position);

            int dist = super.read(readBuffer);

            // EOF
            if (dist < 0) return null;

            readBuffer.flip();

            skipTo(index);
        } else if (!checkCapacityAndGrow()) {
            readBuffer.compact();
            int dist = super.read(readBuffer);
            if (dist < 0) {
                return null;
            }
            readBuffer.flip();
        }

        if (checkCapacityAndGrow()) {
            this.index = index + 1;
            return parse(readBuffer);
        }
        return null;
    }

    /**
     * Batch getting
     *
     * @param fromIndex the index of the first element, inclusive, to be sorted
     * @param array     Destination array
     * @return The number of string read, or -1 if the end of the
     * stream has been reached
     * @throws IOException if I/O error occur
     */
    public int get(int fromIndex, String[] array) throws IOException {
        checkBound(fromIndex);
        boolean write;
        if ((write = status == WRITE) || fromIndex != this.index) {
            if (write) {
                super.mark();
                status = READ;
            }
            long position = getIndexPosition(fromIndex);
            readBuffer.clear();

            super.skip(position);

            int dist = super.read(readBuffer);
            // EOF
            if (dist < 0) return 0;
            readBuffer.flip();
        }
        int i = 0;
        A: for ( ; ; ) {

            if (i == 0 && fromIndex != this.index) {
                skipTo(fromIndex);
            }

            for ( ; checkCapacityAndGrow(); ) {
                array[i++] = parse(readBuffer);
                if (i >= array.length) break A;
            }

            readBuffer.compact();
            // Read more
            int dist = super.read(readBuffer);
            // EOF
            if (dist < 0) break;
            readBuffer.flip();
        }

        this.index = fromIndex + i;

        return i;
    }

    /**
     * Write buffered data to channel
     *
     * @throws IOException if I/O error occur
     */
    private void flush() throws IOException {
        buffer.flip();
        if (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    /**
     * Puts the main's position into index file if need.
     *
     * @throws IOException if I/O error occur
     */
    private void putsIndex() throws IOException {
        // Check status
        if (status == READ) {
            status = WRITE;
            super.reset();
        }
        int size = size();
        // Cache position every 64 records
        if ((size & kSplit) == size) {
            /*
            Flush buffer when it full. The type of position in
            channel is long, so here is compared with the length
            of the long(8 bytes in JAVA).
             */
            if (buffer.remaining() < 8) {
                flush();
            }
            /*
            The main channel header 4 bytes to save the record size,
             so subtract 4 here
             */
            buffer.putLong(super.position() - 4);
        }
    }

    /**
     * Check the getting index
     *
     * @param index the getting index
     */
    private void checkBound(int index) {
        int size = size();
        if (size <= index) {
            throw new ExcelWriteException("index: " + index + ", size: " + size);
        }
    }

    /**
     * Calculate the position according to the subscript recorded in
     * the SharedStringTable.
     * <p>
     * The index position is {@code keyIndex / kSplit * sizeOf(long)},
     * get the position of the string record through the index position
     *
     * @param keyIndex the record's index in {@link SharedStringTable}
     * @return the index block's position
     * @throws IOException if I/O error occur
     */
    private long getIndexPosition(int keyIndex) throws IOException {
        long position = 0L;
        if (keyIndex < (1 << ssst)) return position;
        long index_size = channel.size();
        // Read from file
        if (index_size >> 3 > (keyIndex >> ssst)) {
            flush();
            long pos = channel.position();
            channel.position(keyIndex >> ssst << 3);
            channel.read(buffer);
            buffer.flip();
            position = buffer.getLong();
            channel.position(pos);
            buffer.clear();

            // Read from buffer
        } else {
            int _pos = buffer.position();
            buffer.flip();
            if (buffer.hasRemaining()) {
                buffer.position(((int)((keyIndex >> ssst) - (index_size >> 3))) << 3);
                position = buffer.getLong();
            }
            // Mark status WRITE
            buffer.position(_pos);
            buffer.limit(buffer.capacity());
        }

        return position;
    }

    // Parse string record
    private String parse(ByteBuffer readBuffer) {
        int n = readBuffer.getInt();
        if (bytes == null || bytes.length < n) {
            bytes = new byte[Math.max(n, 128)];
        }
        if (n < 0) {
            char c = (char) ~n;
            if (c < 0xFFFF) {
                chars[0] = c;
                return new String(chars);
            } else return "";
        } else {
            readBuffer.get(bytes, 0, n);
            return new String(bytes, 0, n, UTF_8);
        }
    }

    private void skipTo(int index) {
        for (int n, i = index >> ssst << ssst; i < index; i++) {
            n = readBuffer.getInt();
            readBuffer.position(readBuffer.position() + 2 + n);
        }
    }

    @Override
    public void close() throws IOException {
        buffer = null;
        readBuffer = null;
        if (channel != null) {
            channel.close();
        }
        if (shouldDelete) {
            FileUtil.rm(temp);
        }

        super.close();
    }

    /**
     * Check remaining data and grow if shortage
     *
     * @return true/false
     */
    @Override
    protected boolean checkCapacityAndGrow() {
        int i = hasFullValue(readBuffer);
        if (i < 0) {
            this.readBuffer = grow(readBuffer);
            this.readBuffer.flip();
        }

        return i > 0;
    }
}
