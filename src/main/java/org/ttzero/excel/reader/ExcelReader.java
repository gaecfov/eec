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
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.ttzero.excel.annotation.NS;
import org.ttzero.excel.annotation.TopNS;
import org.ttzero.excel.entity.Relationship;
import org.ttzero.excel.entity.style.Styles;
import org.ttzero.excel.manager.Const;
import org.ttzero.excel.manager.ExcelType;
import org.ttzero.excel.manager.RelManager;
import org.ttzero.excel.manager.docProps.App;
import org.ttzero.excel.manager.docProps.Core;
import org.ttzero.excel.util.FileUtil;
import org.ttzero.excel.util.StringUtil;
import org.ttzero.excel.util.ZipUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.ttzero.excel.reader.SharedStrings.toInt;
import static org.ttzero.excel.util.StringUtil.isNotEmpty;

/**
 * Excel Reader tools
 *
 * A streaming operation chain, using cursor control, the cursor
 * will only move forward, so you cannot repeatedly operate the
 * same Sheet stream. If you need to read the data of a worksheet
 * multiple times, please call the {@link Sheet#reset} method.
 * The internal Row object of the same Sheet page is memory shared,
 * so don't directly convert Stream&lt;Row&gt; to a collection class.
 * You should first consider using try-with-resource to use Reader
 * or manually close the ExcelReader.
 * <blockquote><pre>
 * try (ExcelReader reader = ExcelReader.read(path)) {
 *     reader.sheets().flatMap(Sheet::rows).forEach(System.out::println);
 * } catch (IOException e) {}</pre></blockquote>
 * Create by guanquan.wang on 2018-09-22
 */
public class ExcelReader implements AutoCloseable {
    private Logger LOGGER = LogManager.getLogger(getClass());

    protected ExcelReader() { }

    protected Path self;
    protected Sheet[] sheets;
    private Path temp;
    private ExcelType type;
    private AppInfo appInfo;
    boolean hasFormula;

    /**
     * The Shared String Table
     */
    private SharedStrings sst;

    /**
     * The {@link Styles}
     */
    private Styles styles;

    /**
     * Constructor Excel Reader
     *
     * @param path the excel path
     * @return the {@link ExcelReader}
     * @throws IOException if path not exists or I/O error occur
     */
    public static ExcelReader read(Path path) throws IOException {
        return read(path, 0, 0);
    }

    /**
     * Constructor Excel Reader
     *
     * @param stream the {@link InputStream} of excel
     * @return the {@link ExcelReader}
     * @throws IOException if I/O error occur
     */
    public static ExcelReader read(InputStream stream) throws IOException {
        return read(stream, 0, 0);
    }

    /**
     * Constructor Excel Reader
     *
     * @param path       the excel path
     * @param bufferSize the {@link SharedStrings} buffer size. default is 512
     *                   This parameter affects the number of read times.
     * @return the {@link ExcelReader}
     * @throws IOException if path not exists or I/O error occur
     */
    public static ExcelReader read(Path path, int bufferSize) throws IOException {
        return read(path, bufferSize, 0);
    }

    /**
     * Constructor Excel Reader
     *
     * @param stream     the {@link InputStream} of excel
     * @param bufferSize the {@link SharedStrings} buffer size. default is 512
     *                   This parameter affects the number of read times.
     * @return the {@link ExcelReader}
     * @throws IOException if I/O error occur
     */
    public static ExcelReader read(InputStream stream, int bufferSize) throws IOException {
        return read(stream, bufferSize, 0);
    }

    /**
     * Constructor Excel Reader
     *
     * @param path       the excel path
     * @param bufferSize the {@link SharedStrings} buffer size. default is 512
     *                   This parameter affects the number of read times.
     * @param cacheSize  the {@link Cache} size, default is 512
     * @return the {@link ExcelReader}
     * @throws IOException if path not exists or I/O error occur
     */
    public static ExcelReader read(Path path, int bufferSize, int cacheSize) throws IOException {
        return read(path, bufferSize, cacheSize, false);
    }

    /**
     * Constructor Excel Reader
     *
     * @param stream     the {@link InputStream} of excel
     * @param bufferSize the {@link SharedStrings} buffer size. default is 512
     *                   This parameter affects the number of read times.
     * @param cacheSize  the {@link Cache} size, default is 512
     * @return the {@link ExcelReader}
     * @throws IOException if I/O error occur
     */
    public static ExcelReader read(InputStream stream, int bufferSize, int cacheSize) throws IOException {
        Path temp = FileUtil.mktmp(Const.EEC_PREFIX);
        if (temp == null) {
            throw new IOException("Create temp directory error. Please check your permission");
        }
        FileUtil.cp(stream, temp);
        return read(temp, bufferSize, cacheSize, true);
    }

    /**
     * Type of excel
     *
     * @return enum type ExcelType
     */
    public ExcelType getType() {
        return type;
    }

    /**
     * to streams
     *
     * @return {@link Stream} of {@link Sheet}
     */
    public Stream<Sheet> sheets() {
        Iterator<Sheet> iter = new Iterator<Sheet>() {
            int n = 0;

            @Override
            public boolean hasNext() {
                return n < sheets.length;
            }

            @Override
            public Sheet next() {
                try {
                    // test and load sheet data
                    return sheets[n++].load();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return StreamSupport.stream(Spliterators.spliterator(iter, sheets.length
            , Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    /**
     * get by index
     *
     * @param index sheet index of workbook
     * @return sheet
     */
    public Sheet sheet(int index) {
        try {
            return sheets[index].load(); // lazy loading worksheet data
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * get by name
     *
     * @param sheetName name
     * @return null if not found
     */
    public Sheet sheet(String sheetName) {
        try {
            for (Sheet t : sheets) {
                if (sheetName.equals(t.getName())) {
                    return t.load(); // lazy loading worksheet data
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    /**
     * get all sheets
     *
     * @return Sheet Array
     */
    public Sheet[] all() {
        return sheets;
    }

    /**
     * size of sheets
     *
     * @return int
     */
    public int getSize() {
        return sheets != null ? sheets.length : 0;
    }

    /**
     * close stream and delete temp files
     *
     * @throws IOException when fail close readers
     */
    public void close() throws IOException {
        // Close all opened sheet
        for (Sheet st : sheets) {
            st.close();
        }

        // Close Shared String Table
        if (sst != null)
            sst.close();

        // Delete temp files
        FileUtil.rm_rf(self.toFile(), true);
        if (temp != null) {
            FileUtil.rm(temp);
        }
    }

    /**
     * General information like title,subject and creator
     *
     * @return the information
     */
    public AppInfo getAppInfo() {
        return appInfo;
    }

    /**
     * Check current workbook has formula
     *
     * @return boolean
     */
    public boolean hasFormula() {
        return hasFormula;
    }

    /**
     * Make the reader parse formula
     *
     * @return self
     */
    public ExcelReader parseFormula() {
        for (Sheet sheet : sheets) {
            sheet.parseFormula();
        }
        return this;
    }

    // --- PRIVATE FUNCTIONS


    private ExcelReader(Path path, int bufferSize, int cacheSize) throws IOException {
        // Store template stream as zip file
        Path tmp = FileUtil.mktmp(Const.EEC_PREFIX);
        ZipUtil.unzip(Files.newInputStream(path), tmp);

        // Check the file format and parse general information
        try {
            appInfo = getGeneralInfo(tmp);
        } catch (Exception e) {
            FileUtil.rm_rf(tmp.toFile(), true);
            throw e;
        }

        // load workbook.xml
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(Files.newInputStream(tmp.resolve("xl/_rels/workbook.xml.rels")));
        } catch (DocumentException | IOException e) {
            FileUtil.rm_rf(tmp.toFile(), true);
            throw new ExcelReadException("The file format is incorrect or corrupted. [xl/_rels/workbook.xml.rels]");
        }
        @SuppressWarnings("unchecked")
        List<Element> list = document.getRootElement().elements();
        Relationship[] rels = new Relationship[list.size()];
        int i = 0;
        for (Element e : list) {
            rels[i++] = new Relationship(e.attributeValue("Id"), e.attributeValue("Target"), e.attributeValue("Type"));
        }
        RelManager relManager = RelManager.of(rels);

        try {
            document = reader.read(Files.newInputStream(tmp.resolve("xl/workbook.xml")));
        } catch (DocumentException | IOException e) {
            // read style file fail.
            FileUtil.rm_rf(tmp.toFile(), true);
            throw new ExcelReadException("The file format is incorrect or corrupted. [xl/workbook.xml]");
        }
        Element root = document.getRootElement();
        Namespace ns = root.getNamespaceForPrefix("r");

        // Load SharedString
        Path ss = tmp.resolve("xl/sharedStrings.xml");
        if (Files.exists(ss)) {
            sst = new SharedStrings(ss, bufferSize, cacheSize).load();
        }

        // Load Styles
        Path s = tmp.resolve("xl/styles.xml");
        if (Files.exists(s)) {
            styles = Styles.load(s);
        } else {
            FileUtil.rm_rf(tmp.toFile(), true);
            throw new ExcelReadException("The file format is incorrect or corrupted. [xl/styles.xml]");
        }

        List<Sheet> sheets = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Iterator<Element> sheetIter = root.element("sheets").elementIterator();
        for (; sheetIter.hasNext(); ) {
            Element e = sheetIter.next();
            XMLSheet sheet = new XMLSheet();
            sheet.setName(e.attributeValue("name"));
            sheet.setIndex(Integer.parseInt(e.attributeValue("sheetId")));
            String state = e.attributeValue("state");
            sheet.setHidden("hidden".equals(state));
            Relationship r = relManager.getById(e.attributeValue(QName.get("id", ns)));
            if (r == null) {
                FileUtil.rm_rf(tmp.toFile(), true);
                sheet.close();
                throw new ExcelReadException("The file format is incorrect or corrupted.");
            }
            sheet.setPath(tmp.resolve("xl").resolve(r.getTarget()));
            // put shared string
            sheet.setSst(sst);
            // Setting styles
            sheet.setStyles(styles);
            sheets.add(sheet);
        }

        if (sheets.isEmpty()) {
            FileUtil.rm_rf(tmp.toFile(), true);
            throw new ExcelReadException("The file format is incorrect or corrupted. [There has no worksheet]");
        }

        // Formula string if exists
        Path calcPath = tmp.resolve("xl/calcChain.xml");
        long[][] calcArray = null;
        if (Files.exists(calcPath)) {
            calcArray = parseCalcChain(calcPath, sheets.size());
        }

        // sort by sheet index
        sheets.sort(Comparator.comparingInt(Sheet::getIndex));

        Sheet[] sheets1 = new Sheet[sheets.size()];
        sheets.toArray(sheets1);
        if (calcArray != null) {
            i = 0;
            for (; i < sheets1.length; i++) {
                ((XMLSheet) sheets1[i]).setCalc(calcArray[i]);
            }
            hasFormula = true;
        }

        this.sheets = sheets1;
        self = tmp;
    }

    /**
     * Constructor Excel Reader
     *
     * @param path       the excel path
     * @param bufferSize the {@link SharedStrings} buffer size. default is 512
     *                   This parameter affects the number of read times.
     * @param cacheSize  the {@link Cache} size, default is 512
     * @param rmSource   remove the source files
     * @return the {@link ExcelReader}
     * @throws IOException if path not exists or I/O error occur
     */
    private static ExcelReader read(Path path, int bufferSize, int cacheSize, boolean rmSource) throws IOException {
        // Check document type
        ExcelType type = getType(path);
        ExcelReader er;
        switch (type) {
            case XLSX:
                er = new ExcelReader(path, bufferSize, cacheSize);
                break;
            case XLS:
                try {
                    Class<?> clazz = Class.forName("org.ttzero.excel.reader.BIFF8Reader");
                    Constructor<?> constructor = clazz.getDeclaredConstructor(Path.class, int.class, int.class);
                    er = (ExcelReader) constructor.newInstance(path, bufferSize, cacheSize);
                } catch (Exception e) {
                    throw new ExcelReadException("Only support read Office Open XML file.", e);
                }
                break;
            default:
                throw new ExcelReadException("Unknown file type.");
        }
        er.type = type;

        // storage source path
        if (rmSource) {
            er.temp = path;
        }

        return er;
    }

    /**
     * Check the documents type
     *
     * @param path documents path
     * @return enum of ExcelType
     */
    private static ExcelType getType(Path path) {
        ExcelType type;
        try (InputStream is = Files.newInputStream(path)) {
            byte[] bytes = new byte[8];
            int len = is.read(bytes);
            type = typeOfStream(bytes, len);
        } catch (IOException e) {
            type = ExcelType.UNKNOWN;
        }
        return type;
    }

    // --- check
    private static ExcelType typeOfStream(byte[] bytes, int size) {
        ExcelType excelType = ExcelType.UNKNOWN;
        int length = Math.min(bytes.length, size);
        if (length < 4)
            return excelType;
        int type;
        type  = bytes[0]  & 0xFF;
        type += (bytes[1] & 0xFF) << 8;
        type += (bytes[2] & 0xFF) << 16;
        type += (bytes[3] & 0xFF) << 24;

        int zip = 0x04034B50;
        int b1  = 0xE011CFD0;
        int b2  = 0xE11AB1A1;

        if (type == zip) {
            excelType = ExcelType.XLSX;
        } else if (type == b1 && length >= 8) {
            type  = bytes[4]  & 0xFF;
            type += (bytes[5] & 0xFF) << 8;
            type += (bytes[6] & 0xFF) << 16;
            type += (bytes[7] & 0xFF) << 24;
            if (type == b2) excelType = ExcelType.XLS;
        }
        return excelType;
    }

    protected AppInfo getGeneralInfo(Path tmp) {
        // load workbook.xml
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(Files.newInputStream(tmp.resolve("docProps/app.xml")));
        } catch (DocumentException | IOException e) {
            throw new ExcelReadException("The file format is incorrect or corrupted. [docProps/app.xml]");
        }
        Element root = document.getRootElement();
        App app = new App();
        app.setCompany(root.elementText("Company"));
        app.setApplication(root.elementText("Application"));
        app.setAppVersion(root.elementText("AppVersion"));

        try {
            document = reader.read(Files.newInputStream(tmp.resolve("docProps/core.xml")));
        } catch (DocumentException | IOException e) {
            throw new ExcelReadException("The file format is incorrect or corrupted. [docProps/core.xml]");
        }
        root = document.getRootElement();
        Core core = new Core();
        Class<Core> clazz = Core.class;
        TopNS topNS = clazz.getAnnotation(TopNS.class);
        String[] prefixs = topNS.prefix(), urls = topNS.uri();
        Field[] fields = clazz.getDeclaredFields();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
        for (Field f : fields) {
            NS ns = f.getAnnotation(NS.class);
            if (ns == null) continue;

            f.setAccessible(true);
            int nsIndex = StringUtil.indexOf(prefixs, ns.value());
            if (nsIndex < 0) continue;

            Namespace namespace = new Namespace(ns.value(), urls[nsIndex]);
            Class<?> type = f.getType();
            String v = root.elementText(new QName(f.getName(), namespace));
            if (type == String.class) {
                try {
                    f.set(core, v);
                } catch (IllegalAccessException e) {
                    LOGGER.warn("Set field ({}) error.", f);
                }
            } else if (type == Date.class) {
                try {
                    f.set(core, format.parse(v));
                } catch (ParseException | IllegalAccessException e) {
                    LOGGER.warn("Set field ({}) error.", f);
                }
            }
        }

        return new AppInfo(app, core);
    }

    /* Parse `calcChain` */
    private long[][] parseCalcChain(Path path, int n) {
        Element calcChain;
        try {
            SAXReader reader = new SAXReader();
            calcChain = reader.read(Files.newInputStream(path)).getRootElement();
        } catch (DocumentException | IOException e) {
            LOGGER.warn("Part of `calcChain` has be damaged, It will be ignore all formulas.");
            return null;
        }

        @SuppressWarnings("unchecked")
        Iterator<Element> ite = calcChain.elementIterator();
        int i = 1;
        long[][] array = new long[n][];
        int[] indices = new int[n];
        for (; ite.hasNext(); ) {
            Element e = ite.next();
            String si = e.attributeValue("i"), r = e.attributeValue("r");
            if (isNotEmpty(si)) {
                i = toInt(si.toCharArray(), 0, si.length());
            }
            if (isNotEmpty(r)) {
                long[] sub = array[i - 1];
                if (sub == null) {
                    sub = new long[10];
                    array[i - 1] = sub;
                }

                if (++indices[i - 1] > sub.length) {
                    long[] _sub = new long[sub.length << 1];
                    System.arraycopy(sub, 0, _sub, 0, sub.length);
                    array[i - 1] = sub = _sub;
                }
                sub[indices[i - 1] - 1] = cellRangeToLong(r);
            }
        }

        i = 0;
        for (; i < n; i++) {
            if (indices[i] > 0) {
                long[] a = Arrays.copyOf(array[i], indices[i]);
                Arrays.sort(a);
                array[i] = a;
            } else array[i] = null;
        }
        return array;
    }

    /**
     * Cell range string convert to long
     * 0-16: column number
     * 17-48: row number
     * <blockquote><pre>
     * range string| long value
     * ------------|------------
     * A1          | 65537
     * AA10        | 655387
     * </pre></blockquote>
     * @param r the range string of cell
     * @return long value
     */
    public static long cellRangeToLong(String r) {
        char[] values = r.toCharArray();
        long v = 0L;
        int n = 0;
        for (char value : values) {
            if (value >= 'A' && value <= 'Z') {
                v = v * 26 + value - 'A' + 1;
            }
            else if (value >= 'a' && value <= 'z') {
                v = v * 26 + value - 'a' + 1;
            }
            else if (value >= '0') {
                n = n * 10 + value - '0';
            }
            else
                throw new ExcelReadException("Column mark out of range: " + r);
        }
        return (v & 0x7FFF) | ((long) n) << 16;
    }

}