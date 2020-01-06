/*
 * Copyright (c) 2019-2021, guanquan.wang@yandex.com All Rights Reserved.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ttzero.excel.entity.style.Styles;
import org.ttzero.excel.util.StringUtil;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.StringJoiner;

import static org.ttzero.excel.reader.Cell.BLANK;
import static org.ttzero.excel.reader.Cell.BOOL;
import static org.ttzero.excel.reader.Cell.CHARACTER;
import static org.ttzero.excel.reader.Cell.DATE;
import static org.ttzero.excel.reader.Cell.DATETIME;
import static org.ttzero.excel.reader.Cell.DOUBLE;
import static org.ttzero.excel.reader.Cell.INLINESTR;
import static org.ttzero.excel.reader.Cell.LONG;
import static org.ttzero.excel.reader.Cell.NUMERIC;
import static org.ttzero.excel.reader.Cell.SST;
import static org.ttzero.excel.reader.Cell.TIME;
import static org.ttzero.excel.util.DateUtil.toDate;
import static org.ttzero.excel.util.DateUtil.toLocalDate;
import static org.ttzero.excel.util.DateUtil.toTime;
import static org.ttzero.excel.util.DateUtil.toTimestamp;
import static org.ttzero.excel.util.StringUtil.EMPTY;
import static org.ttzero.excel.util.StringUtil.isNotEmpty;

/**
 * Create by guanquan.wang at 2019-04-17 11:08
 */
public abstract class Row {
    protected Logger logger = LogManager.getLogger(getClass());
    // Index to row
    int index = -1;
    // Index to first column (zero base)
    int fc = 0;
    // Index to last column (zero base)
    int lc = -1;
    // Share cell
    Cell[] cells;
    /**
     * The Shared String Table
     */
    SharedStrings sst;
    // The header row
    protected HeaderRow hr;
    boolean unknownLength;

    // Cache formulas
    private PreCalc[] sharedCalc;

    /**
     * The global styles
     */
    Styles styles;

    /**
     * The number of row. (one base)
     *
     * @return int value
     */
    public int getRowNumber() {
        return index;
    }

    /**
     * Returns the index of the first column (zero base)
     *
     * @return the first column index
     */
    public int getFirstColumnIndex() {
        return fc;
    }

    /**
     * Returns the index of the last column (zero base).
     * The last index of column is increment the max available index
     *
     * @return the last column index
     */
    public int getLastColumnIndex() {
        return lc;
    }

    /**
     * Test unused row (not contains any filled or formatted or value)
     *
     * @return true if unused
     */
    public boolean isEmpty() {
        return lc - fc <= 0;
    }

    private String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + lc;
    }

    protected void rangeCheck(int index) {
        if (index >= lc || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * Returns {@link Cell}
     * @param i the position of cell
     * @return the {@link Cell}
     */
    protected Cell getCell(int i) {
        rangeCheck(i);
        return cells[i];
    }

    /**
     * Search {@link Cell} by column name
     *
     * @param name the column name
     * @return the {@link Cell}
     */
    protected Cell getCell(String name) {
        int i = hr.getIndex(name);
        rangeCheck(i);
        return cells[i];
    }

    /**
     * convert row to header_row
     *
     * @return header Row
     */
    public HeaderRow asHeader() {
        HeaderRow hr = HeaderRow.with(this);
        this.hr = hr;
        return hr;
    }

    Row setHr(HeaderRow hr) {
        this.hr = hr;
        return this;
    }

    /**
     * Get boolean value by column index
     *
     * @param columnIndex the cell index
     * @return boolean
     */
    public boolean getBoolean(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getBoolean(c);
    }

    /**
     * Get boolean value by column name
     *
     * @param columnName the cell name
     * @return boolean
     */
    public boolean getBoolean(String columnName) {
        Cell c = getCell(columnName);
        return getBoolean(c);
    }

    /**
     * Get boolean value
     *
     * @param c the {@link Cell}
     * @return boolean
     */
    protected boolean getBoolean(Cell c) {
        boolean v;
        switch (c.t) {
            case BOOL:
                v = c.bv;
                break;
            case NUMERIC:
            case DOUBLE:
                v = c.nv != 0 || c.dv >= 0.000001 || c.dv <= -0.000001;
                break;
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                v = isNotEmpty(c.sv);
                break;
            case INLINESTR:
                v = isNotEmpty(c.sv);
                break;

            default: v = false;
        }
        return v;
    }

    /**
     * Get byte value by column index
     *
     * @param columnIndex the cell index
     * @return byte
     */
    public byte getByte(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getByte(c);
    }

    /**
     * Get byte value by column name
     *
     * @param columnName the cell name
     * @return byte
     */
    public byte getByte(String columnName) {
        Cell c = getCell(columnName);
        return getByte(c);
    }

    /**
     * Get byte value
     *
     * @param c the {@link Cell}
     * @return byte
     */
    protected byte getByte(Cell c) {
        byte b = 0;
        switch (c.t) {
            case NUMERIC:
                b |= c.nv;
                break;
            case LONG:
                b |= c.lv;
                break;
            case BOOL:
                b |= c.bv ? 1 : 0;
                break;
            case DOUBLE:
                b |= (int) c.dv;
                break;
            default: throw new UncheckedTypeException("can't convert to byte");
        }
        return b;
    }

    /**
     * Get char value by column index
     *
     * @param columnIndex the cell index
     * @return char
     */
    public char getChar(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getChar(c);
    }

    /**
     * Get char value by column name
     *
     * @param columnName the cell name
     * @return char
     */
    public char getChar(String columnName) {
        Cell c = getCell(columnName);
        return getChar(c);
    }

    /**
     * Get char value
     *
     * @param c the {@link Cell}
     * @return char
     */
    protected char getChar(Cell c) {
        char cc = 0;
        switch (c.t) {
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                if (isNotEmpty(c.sv)) {
                    cc |= c.sv.charAt(0);
                }
                break;
            case INLINESTR:
                if (isNotEmpty(c.sv)) {
                    cc |= c.sv.charAt(0);
                }
                break;
            case NUMERIC:
                cc |= c.nv;
                break;
            case LONG:
                cc |= c.lv;
                break;
            case BOOL:
                cc |= c.bv ? 1 : 0;
                break;
            case DOUBLE:
                cc |= (int) c.dv;
                break;
            default: throw new UncheckedTypeException("can't convert to char");
        }
        return cc;
    }

    /**
     * Get short value by column index
     *
     * @param columnIndex the cell index
     * @return short
     */
    public short getShort(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getShort(c);
    }

    /**
     * Get short value by column name
     *
     * @param columnName the cell name
     * @return short
     */
    public short getShort(String columnName) {
        Cell c = getCell(columnName);
        return getShort(c);
    }

    /**
     * Get short value
     *
     * @param c the {@link Cell}
     * @return short
     */
    protected short getShort(Cell c) {
        short s = 0;
        switch (c.t) {
            case NUMERIC:
                s |= c.nv;
                break;
            case LONG:
                s |= c.lv;
                break;
            case BOOL:
                s |= c.bv ? 1 : 0;
                break;
            case DOUBLE:
                s |= (int) c.dv;
                break;
            default: throw new UncheckedTypeException("can't convert to short");
        }
        return s;
    }

    /**
     * Get int value by column index
     *
     * @param columnIndex the cell index
     * @return int
     */
    public int getInt(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getInt(c);
    }

    /**
     * Get int value by column name
     *
     * @param columnName the cell name
     * @return int
     */
    public int getInt(String columnName) {
        Cell c = getCell(columnName);
        return getInt(c);
    }

    /**
     * Get int value
     *
     * @param c the {@link Cell}
     * @return int
     */
    protected int getInt(Cell c) {
        int n;
        switch (c.t) {
            case NUMERIC:
                n = c.nv;
                break;
            case LONG:
                n = (int) c.lv;
                break;
            case DOUBLE:
                n = (int) c.dv;
                break;
            case BOOL:
                n = c.bv ? 1 : 0;
                break;
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                n = Integer.parseInt(c.sv);
                break;
            case INLINESTR:
                n = Integer.parseInt(c.sv);
                break;

            default: throw new UncheckedTypeException("unknown type");
        }
        return n;
    }

    /**
     * Get long value by column index
     *
     * @param columnIndex the cell index
     * @return long
     */
    public long getLong(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getLong(c);
    }

    /**
     * Get long value by column name
     *
     * @param columnName the cell name
     * @return long
     */
    public long getLong(String columnName) {
        Cell c = getCell(columnName);
        return getLong(c);
    }

    /**
     * Get long value
     *
     * @param c the {@link Cell}
     * @return long
     */
    protected long getLong(Cell c) {
        long l;
        switch (c.t) {
            case LONG:
                l = c.lv;
                break;
            case NUMERIC:
                l = c.nv;
                break;
            case DOUBLE:
                l = (long) c.dv;
                break;
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                l = Long.parseLong(c.sv);
                break;
            case INLINESTR:
                l = Long.parseLong(c.sv);
                break;
            case BOOL:
                l = c.bv ? 1L : 0L;
                break;
            default: throw new UncheckedTypeException("unknown type");
        }
        return l;
    }

    /**
     * Get string value by column index
     *
     * @param columnIndex the cell index
     * @return string
     */
    public String getString(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getString(c);
    }

    /**
     * Get string value by column name
     *
     * @param columnName the cell name
     * @return string
     */
    public String getString(String columnName) {
        Cell c = getCell(columnName);
        return getString(c);
    }

    /**
     * Get string value
     *
     * @param c the {@link Cell}
     * @return string
     */
    protected String getString(Cell c) {
        String s;
        switch (c.t) {
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                s = c.sv;
                break;
            case INLINESTR:
                s = c.sv;
                break;
            case BLANK:
                s = EMPTY;
                break;
            case LONG:
                s = String.valueOf(c.lv);
                break;
            case NUMERIC:
                s = String.valueOf(c.nv);
                break;
            case DOUBLE:
                s = String.valueOf(c.dv);
                break;
            case BOOL:
                s = c.bv ? "true" : "false";
                break;
            default: s = c.sv;
        }
        return s;
    }

    /**
     * Get float value by column index
     *
     * @param columnIndex the cell index
     * @return float
     */
    public float getFloat(int columnIndex) {
        return (float) getDouble(columnIndex);
    }

    /**
     * Get float value by column index
     *
     * @param columnName the cell index
     * @return float
     */
    public float getFloat(String columnName) {
        return (float) getDouble(columnName);
    }

    /**
     * Get double value by column index
     *
     * @param columnIndex the cell index
     * @return double
     */
    public double getDouble(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getDouble(c);
    }

    /**
     * Get double value by column name
     *
     * @param columnName the cell name
     * @return double
     */
    public double getDouble(String columnName) {
        Cell c = getCell(columnName);
        return getDouble(c);
    }

    /**
     * Get double value
     *
     * @param c the {@link Cell}
     * @return double
     */
    protected double getDouble(Cell c) {
        double d;
        switch (c.t) {
            case DOUBLE:
                d = c.dv;
                break;
            case NUMERIC:
                d = c.nv;
                break;
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                d = Double.valueOf(c.sv);
                break;
            case INLINESTR:
                d = Double.valueOf(c.sv);
                break;

            default: throw new UncheckedTypeException("unknown type");
        }
        return d;
    }

    /**
     * Get decimal value by column index
     *
     * @param columnIndex the cell index
     * @return BigDecimal
     */
    public BigDecimal getDecimal(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getDecimal(c);
    }

    /**
     * Get BigDecimal value by column name
     *
     * @param columnName the cell name
     * @return BigDecimal
     */
    public BigDecimal getDecimal(String columnName) {
        Cell c = getCell(columnName);
        return getDecimal(c);
    }

    /**
     * Get BigDecimal value
     *
     * @param c the {@link Cell}
     * @return BigDecimal
     */
    protected BigDecimal getDecimal(Cell c) {
        BigDecimal bd;
        switch (c.t) {
            case DOUBLE:
                bd = BigDecimal.valueOf(c.dv);
                break;
            case NUMERIC:
                bd = BigDecimal.valueOf(c.nv);
                break;
            default:
                bd = new BigDecimal(c.sv);
        }
        return bd;
    }

    /**
     * Get date value by column index
     *
     * @param columnIndex the cell index
     * @return Date
     */
    public Date getDate(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getDate(c);
    }

    /**
     * Get date value by column name
     *
     * @param columnName the cell name
     * @return Date
     */
    public Date getDate(String columnName) {
        Cell c = getCell(columnName);
        return getDate(c);
    }

    /**
     * Get date value
     *
     * @param c the {@link Cell}
     * @return BigDecimal
     */
    protected Date getDate(Cell c) {
        Date date;
        switch (c.t) {
            case NUMERIC:
                date = toDate(c.nv);
                break;
            case DOUBLE:
                date = toDate(c.dv);
                break;
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                date = toDate(c.sv);
                break;
            case INLINESTR:
                date = toDate(c.sv);
                break;
            default: throw new UncheckedTypeException("");
        }
        return date;
    }

    /**
     * Get timestamp value by column index
     *
     * @param columnIndex the cell index
     * @return java.sql.Timestamp
     */
    public Timestamp getTimestamp(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getTimestamp(c);
    }

    /**
     * Get timestamp value by column name
     *
     * @param columnName the cell name
     * @return java.sql.Timestamp
     */
    public Timestamp getTimestamp(String columnName) {
        Cell c = getCell(columnName);
        return getTimestamp(c);
    }

    /**
     * Get timestamp value
     *
     * @param c the {@link Cell}
     * @return java.sql.Timestamp
     */
    protected Timestamp getTimestamp(Cell c) {
        Timestamp ts;
        switch (c.t) {
            case NUMERIC:
                ts = toTimestamp(c.nv);
                break;
            case DOUBLE:
                ts = toTimestamp(c.dv);
                break;
            case SST:
                if (c.sv == null) {
                    c.setSv(sst.get(c.nv));
                }
                ts = toTimestamp(c.sv);
                break;
            case INLINESTR:
                ts = toTimestamp(c.sv);
                break;
            default: throw new UncheckedTypeException("");
        }
        return ts;
    }

    /**
     * Get time value by column index
     *
     * @param columnIndex the cell index
     * @return java.sql.Time
     */
    public java.sql.Time getTime(int columnIndex) {
        Cell c = getCell(columnIndex);
        if (c.t == DOUBLE) {
            return toTime(c.dv);
        }
        throw new UncheckedTypeException("can't convert to java.sql.Time");
    }

    /**
     * Get time value by column name
     *
     * @param columnName the cell name
     * @return java.sql.Time
     */
    public java.sql.Time getTime(String columnName) {
        Cell c = getCell(columnName);
        if (c.t == DOUBLE) {
            return toTime(c.dv);
        }
        throw new UncheckedTypeException("can't convert to java.sql.Time");
    }

    /**
     * Returns formula if exists
     *
     * @param columnIndex the cell index
     * @return the formula string if exists, otherwise return null
     */
    public String getFormula(int columnIndex) {
        Cell c = getCell(columnIndex);
        return c.fv;
    }

    /**
     * Returns formula if exists
     *
     * @param columnName the cell name
     * @return the formula string if exists, otherwise return null
     */
    public String getFormula(String columnName) {
        Cell c = getCell(columnName);
        return c.fv;
    }

    /**
     * Check cell has formula
     *
     * @param columnIndex the cell index
     * @return the formula string if exists, otherwise return null
     */
    public boolean hasFormula(int columnIndex) {
        return getCell(columnIndex).f;
    }

    /**
     * Check cell has formula
     *
     * @param columnName the cell name
     * @return the formula string if exists, otherwise return null
     */
    public boolean hasFormula(String columnName) {
        return getCell(columnName).f;
    }

    /**
     * Returns the type of cell
     *
     * @param columnIndex the cell index from zero
     * @return the {@link CellType}
     */
    public CellType getCellType(int columnIndex) {
        Cell c = getCell(columnIndex);
        return getCellType(c);
    }

    /**
     * Returns the type of cell
     *
     * @param columnName the cell name
     * @return the {@link CellType}
     */
    public CellType getCellType(String columnName) {
        Cell c = getCell(columnName);
        return getCellType(c);
    }

    /**
     * Returns the type of cell
     *
     * @param c the {@link Cell}
     * @return the {@link CellType}
     */
    protected CellType getCellType(Cell c) {
        CellType type;
        switch (c.t) {
            case SST:
            case INLINESTR:
                type = CellType.STRING;
                break;
            case NUMERIC:
            case CHARACTER:
                type = !styles.fastTestDateFmt(c.xf) ? CellType.INTEGER : CellType.DATE;
                break;
            case LONG:
                type = CellType.LONG;
                break;
            case DOUBLE:
                type = !styles.fastTestDateFmt(c.xf) ? CellType.DOUBLE : CellType.DATE;
                break;
            case BOOL:
                type = CellType.BOOLEAN;
                break;
            case DATETIME:
            case DATE:
            case TIME:
                type = CellType.DATE;
                break;
            case BLANK:
                type = CellType.BLANK;
                break;

            default: type = CellType.STRING;
        }
        return type;
    }

    /**
     * Returns the binding type if is bound, otherwise returns Row
     *
     * @param <T> the type of binding
     * @return T
     */
    @SuppressWarnings("unchecked")
    public <T> T get() {
        if (hr != null && hr.getClazz() != null) {
            T t;
            try {
                t = (T) hr.getClazz().newInstance();
                hr.put(this, t);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new UncheckedTypeException(hr.getClazz() + " new instance error.", e);
            }
            return t;
        } else return (T) this;
    }

    /**
     * Returns the binding type if is bound, otherwise returns Row
     *
     * @param <T> the type of binding
     * @return T
     */
    @SuppressWarnings("unchecked")
    public <T> T geet() {
        if (hr != null && hr.getClazz() != null) {
            T t = hr.getT();
            try {
                hr.put(this, t);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new UncheckedTypeException("call set method error.", e);
            }
            return t;
        } else return (T) this;
    }
    /////////////////////////////To object//////////////////////////////////

    /**
     * Convert to object, support annotation
     *
     * @param clazz the type of binding
     * @param <T>   the type of return object
     * @return T
     */
    public <T> T to(Class<T> clazz) {
        if (hr == null) {
            throw new UncheckedTypeException("Lost header row info");
        }
        // reset class info
        if (!hr.is(clazz)) {
            hr.setClass(clazz);
        }
        T t;
        try {
            t = clazz.newInstance();
            hr.put(this, t);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new UncheckedTypeException(clazz + " new instance error.", e);
        }
        return t;
    }

    /**
     * Convert to T object, support annotation
     * the is a memory shared object
     *
     * @param clazz the type of binding
     * @param <T>   the type of return object
     * @return T
     */
    public <T> T too(Class<T> clazz) {
        if (hr == null) {
            throw new UncheckedTypeException("Lost header row info");
        }
        // reset class info
        if (!hr.is(clazz)) {
            try {
                hr.setClassOnce(clazz);
            } catch (IllegalAccessException | InstantiationException e) {
                throw new UncheckedTypeException(clazz + " new instance error.", e);
            }
        }
        T t = hr.getT();
        try {
            hr.put(this, t);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new UncheckedTypeException("call set method error.", e);
        }
        return t;
    }

    @Override
    public String toString() {
        if (isEmpty()) return "";
        StringJoiner joiner = new StringJoiner(" | ");
        // show row number
//        joiner.add(String.valueOf(getRowNumber()));
        for (int i = fc; i < lc; i++) {
            Cell c = cells[i];
            switch (c.t) {
                case SST:
                    if (c.sv == null) {
                        c.setSv(sst.get(c.nv));
                    }
                    joiner.add(c.sv);
                    break;
                case INLINESTR:
                    joiner.add(c.sv);
                    break;
                case BOOL:
                    joiner.add(String.valueOf(c.bv));
                    break;
//                case FUNCTION: // convert to inner string
//                    joiner.add("<function>");
//                    break;
                case NUMERIC:
                    if (!styles.fastTestDateFmt(c.xf)) joiner.add(String.valueOf(c.nv));
                    else joiner.add(toLocalDate(c.nv).toString());
                    break;
                case LONG:
                    joiner.add(String.valueOf(c.lv));
                    break;
                case DOUBLE:
                    if (!styles.fastTestDateFmt(c.xf)) joiner.add(String.valueOf(c.dv));
                    else joiner.add(toTimestamp(c.dv).toString());
                    break;
                case BLANK:
                    joiner.add(EMPTY);
                default:
                    joiner.add(null);
            }
        }
        return joiner.toString();
    }

    /**
     * Add function shared ref
     * <blockquote><pre>
     * 63   : Not used
     * 42-62: First row number
     * 28-41: First column number
     * 8-27/14-27: Size, if axis is zero the size used 20 bits, otherwise used 14 bits
     * 2-7/2-13: Not used
     * 0-1    : Axis, 00: range 01: y-axis 10: x-axis
     * </pre></blockquote>
     *
     * @param i the ref id
     * @param ref ref value, a range dimension string
     */
    void addRef(int i, String ref) {
        if (StringUtil.isEmpty(ref) || ref.indexOf(':') < 0)
            return;

        if (sharedCalc == null) {
            sharedCalc = new PreCalc[Math.max(10, i + 1)];
        } else if (i >= sharedCalc.length) {
            sharedCalc = Arrays.copyOf(sharedCalc, i + 10);
        }
        Dimension dim = Dimension.from(ref);

        long l = 0;
        l |= (long) (dim.firstRow & (1 << 20) - 1) << 42;
        l |= (long) (dim.firstColumn & (1 << 14) - 1) << 28;

        if (dim.firstColumn == dim.lastColumn) {
            l |= ((dim.lastRow - dim.firstRow) & (1 << 20) - 1) << 8;
            l |= (1 << 1);
        }
        else if (dim.firstRow == dim.lastRow) {
            l |= ((dim.lastColumn - dim.firstColumn) & (1 << 14) - 1) << 14;
            l |= 1;
        }
        sharedCalc[i] = new PreCalc(l);
    }

    /**
     * Setting calc string
     *
     * @param i the ref id
     * @param calc the calc string
     */
    void setCalc(int i, String calc) {
        if (sharedCalc == null || sharedCalc.length <= i
            || sharedCalc[i] == null || StringUtil.isEmpty(calc))
            return;

        sharedCalc[i].setCalc(calc.toCharArray());
    }

    /**
     * Get calc string by ref id and coordinate
     *
     * @param i the ref id
     * @param coordinate the cell coordinate
     * @return calc string
     */
    String getCalc(int i, long coordinate) {
        // Index out of range
        if (sharedCalc == null || sharedCalc.length <= i
            || sharedCalc[i] == null)
            return EMPTY;

        return sharedCalc[i].get(coordinate);
    }

    /**
     * Convert to column index
     *
     * @param cb character buffer
     * @param a the start index
     * @param b the end index
     * @return the cell index
     */
    public static int toCellIndex(char[] cb, int a, int b) {
        int n = 0;
        for (; a <= b; a++) {
            if (cb[a] <= 'Z' && cb[a] >= 'A') {
                n = n * 26 + cb[a] - '@';
            } else if (cb[a] <= 'z' && cb[a] >= 'a') {
                n = n * 26 + cb[a] - '、';
            } else break;
        }
        return n;
    }
}