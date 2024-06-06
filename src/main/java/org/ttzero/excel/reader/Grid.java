/*
 * Copyright (c) 2017-2020, guanquan.wang@yandex.com All Rights Reserved.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;

import static java.lang.Integer.numberOfTrailingZeros;
import static org.ttzero.excel.util.StringUtil.formatBinarySize;

/**
 * @author guanquan.wang at 2020-01-09 16:54
 */
public interface Grid {

    /**
     * Mark `1` at the specified coordinates
     *
     * @param coordinate the excel coordinate string,
     *                   it's a coordinate or range coordinates
     *                   like `A1` or `A1:C4`
     */
    default void mark(String coordinate) {
        mark(Dimension.of(coordinate));
    }

    /**
     * Mark `1` at the specified coordinates
     *
     * @param chars the excel coordinate buffer,
     *              it's a coordinate or range coordinates
     *              like `A1` or `A1:C4`
     * @param from  the begin index
     * @param to    the end index
     */
    default void mark(char[] chars, int from, int to) {
        mark(Dimension.of(new String(chars, from, to - from + 1)));
    }

    /**
     * Mark `1` at the specified {@link Dimension}
     *
     * @param dimension range {@link Dimension}
     */
    void mark(Dimension dimension);

    /**
     * Test the specified {@link Dimension} has be marked.
     *
     * @param r row number (from one)
     * @param c column number (from one)
     * @return true if the specified dimension has be marked
     */
    boolean test(int r, int c);

    /**
     * Merge cell in range cells
     *
     * @param r row number (from one)
     * @param cell column number (from one)
     * @return not a mered cell will returns 0, 1 means current cell is the first merged cells otherwise returns 2
     */
    int merge(int r, Cell cell);

    /**
     * Returns count of merged cells
     *
     * @return count of merged cells
     */
    int size();


    /**
     * Use binary to mark whether the cells are `merged` and set
     * them accordingly if they are merged, so that you can quickly
     * mark and check the cell status and save space.
     */
    final class FastGrid implements Grid {
        private final int fr, fc, lr, lc; // Start index of Row and Column(One base)
        private final long[] g;

        private final int c;
        private final Scanner scanner;

        FastGrid(Dimension dim) {
            fr = dim.firstRow;
            lr = Math.max(dim.firstRow, dim.lastRow);
            fc = dim.firstColumn;
            lc = Math.max(dim.firstColumn, dim.lastColumn);

            // Power of two minus 1
            int nc = lc - fc + 1, nr = lr - fr + 1, b = powerOneBit(nc);
            c = numberOfTrailingZeros(b + 1) + (isPowerOfTwo(nc) ? -1 : 0);
            int n = 6 - c, len = nr >> n;
            g = new long[len > 0 ? nr > (len << n) ? len + 1 : len : 1];

            scanner = new FastLinkedScanner();
        }

        static int powerOneBit(int i) {
            i |= (i >>  1);
            i |= (i >>  2);
            i |= (i >>  4);
            i |= (i >>  8);
            i |= (i >> 16);
            return i;
        }

        static boolean isPowerOfTwo(int n) {
            return (n & -n) == n; // OR (n & n - 1) == 0;
        }

        @Override
        public void mark(Dimension dimension) {
            int n = dimension.lastColumn - dimension.firstColumn + 1
                , p = 1 << (6 - c);
            long l = ~(~0L >> n << n) << (dimension.firstColumn - fc);
            for (int i = dimension.firstRow; i <= dimension.lastRow; i++)
                g[getRow(i)] |= l << ((p - ((i - fr + 1) & (p - 1))) << c);

            // Create index on the first axis
            scanner.put(new LinkedScanner.E(dimension, new Cell()));
        }

        @Override
        public boolean test(int r, int c) {
            if (!range(r, c)) return false;
            long l = g[getRow(r)];
            int p = 1 << (6 - this.c);
            l >>= ((p - ((r - fr + 1) & (p - 1))) << this.c);
            l >>= (c - fc);
            return (l & 1) == 1;
        }

        @Override
        public int merge(int r, Cell cell) {
            Scanner.Entry e;
            if (!test(r, cell.i) || (e = scanner.get(r, cell.i)) == null) return 0;

            Dimension dim = e.getDim();
            int i = 2;
            if (dim.firstRow != r || dim.firstColumn != cell.i) {
                // Copy value from the first merged cell
                cell.from(e.getCell());
            }
            // First merged cell
            else {
                e.getCell().from(cell);
                i = 1;
            }
            return i;
        }

        @Override
        public int size() {
            return scanner.size();
        }

        boolean range(int r, int c) {
            return r >= fr && r <= lr && c >= fc && c <= lc;
        }

        int getRow(int i) {
            return (i - fr) >> (6 - c);
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner("\n");
            joiner.add(getClass().getSimpleName() + " Size: " + formatBinarySize(g.length << 3));
            int last = lr - fr + 1, j = 0;
            A: for (long l : g) {
                String s = append(Long.toBinaryString(l));
                for (int i = 0, n = 1 << 6 - c; i < n; i++) {
                    joiner.add(s.substring(i << c, (i + 1) << c));
                    if (++j >= last) break A;
                }
            }
            return joiner.toString();
        }

        private final char[] chars = new char[64];

        private String append(String s) {
            int n = s.length();
            s.getChars(0, n, chars, chars.length - n);
            Arrays.fill(chars, 0, chars.length - n, '0');
            return new String(chars);
        }

        private static class FastLinkedScanner extends LinkedScanner {
            @Override
            public Entry get(int r, int c) {
                if (size() == 1) return head.entry;
                return super.get(r, c);
            }
        }
    }

    final class IndexGrid implements Grid {
        private final int fr, fc, lr, lc; // Start index of Row and Column(One base)
        private final Map<Long, Cell> index;
        private int size;
        private long[] topCells;
        IndexGrid(Dimension dim, int n) {
            fr = dim.firstRow;
            lr = Math.max(dim.firstRow, dim.lastRow);
            fc = dim.firstColumn;
            lc = Math.max(dim.firstColumn, dim.lastColumn);

            index = new HashMap<>(n);
            topCells = new long[1 << 6];
        }

        @Override
        public void mark(Dimension dim) {
            Cell cell = new Cell();
            for (int i = dim.firstRow; i <= dim.lastRow; i++) {
                for (int j = dim.firstColumn; j <= dim.lastColumn; j++) {
                    index.put(((long) i) << 16 | j, cell);
                }
            }

            if (topCells.length <= size) {
                long[] _cells = new long[size + 64];
                System.arraycopy(topCells, 0, _cells, 0, size);
                topCells = _cells;
            }

            long v = ((long) dim.firstRow) << 16 | dim.firstColumn;
            int i = Arrays.binarySearch(topCells, 0, size, v);
            insert(topCells, size, i > 0 ? i : ~i, v);

            size++;
        }

        // Insert value into array, Ignore bound check
        static void insert(long[] array, int size, int index, long value) {
            if (index < size) System.arraycopy(array, index, array, index + 1, size - index);
            array[index] = value;
        }

        @Override
        public boolean test(int r, int c) {
            return range(r, c) && index.containsKey(((long) r) << 16 | c);
        }

        @Override
        public int merge(int r, Cell cell) {
            Cell c;
            long v;
            if ((c = index.get(v = (((long) r) << 16 | cell.i))) == null) return 0;

            int i = 2;
            if (Arrays.binarySearch(topCells, 0, size, v) < 0) {
                // Copy value from the first merged cell
                cell.from(c);
            }
            // Current cell has value
            else {
                c.from(cell);
                i = 1;
            }
            return i;
        }

        @Override
        public int size() {
            return size;
        }

        boolean range(int r, int c) {
            return r >= fr && r <= lr && c >= fc && c <= lc;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " has " + index.size() +" keys";
        }
    }

    final class FractureGrid implements Grid {
        private final int fr, fc, lr, lc; // Start index of Row and Column(One base)
        private final LinkedScanner scanner;

        FractureGrid(Dimension dim) {
            fr = dim.firstRow;
            lr = Math.max(dim.firstRow, dim.lastRow);
            fc = dim.firstColumn;
            lc = Math.max(dim.firstColumn, dim.lastColumn);

            scanner = new LinkedScanner();
        }

        @Override
        public void mark(Dimension dim) {
            scanner.put(new LinkedScanner.E(dim, new Cell()));
        }

        @Override
        public boolean test(int r, int c) {
            return range(r, c) && scanner.get(r, c) != null;
        }

        @Override
        public int merge(int r, Cell cell) {
            Scanner.Entry e;
            if (!range(r, cell.i) || (e = scanner.get(r, cell.i)) == null) return 0;

            Dimension dim = e.getDim();
            int i = 2;
            if (dim.firstRow != r || dim.firstColumn != cell.i) {
                // Copy value from the first merged cell
                cell.from(e.getCell());
            }
            // First merged cell
            else {
                e.getCell().from(cell);
                i = 1;
            }
            return i;
        }

        @Override
        public int size() {
            return scanner.size;
        }

        boolean range(int r, int c) {
            return r >= fr && r <= lr && c >= fc && c <= lc;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " has " + scanner.size() +" dimensions";
        }
    }


    interface Scanner extends Iterable<Scanner.Entry> {

        void put(Entry entry);

        Entry get(int r, int c);

        int size();

        interface Entry {
            Dimension getDim();

            Cell getCell();
        }
    }


    class LinkedScanner implements Scanner {

        final static class E implements Entry {
            private final Dimension dim;
            private final Cell cell;
            private int n;

            E(Dimension dim, Cell cell) {
                this.dim = dim;
                this.cell = cell;
                n = (dim.lastRow - dim.firstRow + 1) * (dim.lastColumn - dim.firstColumn + 1);
            }

            @Override
            public Dimension getDim() {
                return dim;
            }

            @Override
            public Cell getCell() {
                return cell;
            }
        }

        private static class Node {
            private Node next;
            private final E entry;

            Node(E entry, Node next) {
                this.entry = entry;
                this.next = next;
            }
        }

        Node head, tail;
        private int size;

        @Override
        public void put(Entry entry) {
            E e;
            if (entry instanceof E) e = (E) entry;
            else e = new E(entry.getDim(), entry.getCell());

            if (head != null) {
                Node f = head, bf = null;
                for (int p; f != null; f = f.next) {
                    p = f.entry.getDim().firstRow - entry.getDim().firstRow;
                    if (p > 0 || p == 0 && f.entry.getDim().firstColumn > entry.getDim().firstColumn) {
                        Node newNode = new Node(e, f);
                        if (f == head) head = newNode;
                        else bf.next = newNode;
                        break;
                    }
                    bf = f;
                }

                if (f == null) {
                    tail = bf.next = new Node(e, null);
                }
            } else {
                head = tail = new Node(e, null);
            }
            size++;
        }

        @Override
        public Entry get(int r, int c) {
            Node val = null;
            if (head == null) return null;
            Node f = head, bf = null;
            for (; f != null; f = f.next) {
                if (f.entry.getDim().checkRange(r, c)) {
                    val = f;
                    break;
                }
                bf = f;
            }

            if (val != null) {
                int n = --val.entry.n;
                // Insert entry ahead
                if (n > 0 && val != head && val.entry.dim.width > 1) {
                    bf.next = val.next;
                    val.next = head;
                    head = val;
                    // Move entry back
                } else if (n == 0) {
                    if (bf != null) bf.next = val.next;
                    else head = val.next;
                    val.next = null;
                    tail.next = val;
                    tail = val;
                }
            }
            // Not Found, it never occur
            else return null;

            return val.entry;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public Iterator<Entry> iterator() {
            return new ForwardIterator(head);
        }

        private static class ForwardIterator implements Iterator<LinkedScanner.Entry> {
            private Node first;
            ForwardIterator(Node first) {
                this.first = first;
            }

            @Override
            public boolean hasNext() {
                return first != null;
            }
            @Override
            public Entry next() {
                Entry e = first.entry;
                first = first.next;
                return e;
            }
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner("->");

            for (Entry entry : this) {
                joiner.add(entry.getDim().toString());
            }

            return joiner.toString();
        }
    }
}