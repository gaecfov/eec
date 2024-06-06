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

package org.ttzero.excel.entity;

import org.junit.Test;
import org.ttzero.excel.entity.style.Fill;
import org.ttzero.excel.entity.style.PatternType;
import org.ttzero.excel.entity.style.Styles;
import org.ttzero.excel.reader.Drawings;
import org.ttzero.excel.reader.ExcelReader;
import org.ttzero.excel.reader.HeaderRow;
import org.ttzero.excel.reader.Row;
import org.ttzero.excel.reader.Sheet;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ttzero.excel.reader.ExcelReaderTest.testResourceRoot;

/**
 * @author guanquan.wang at 2019-04-29 11:14
 */
public class ListObjectPagingTest extends WorkbookTest {

    @Test public void testPaging() throws IOException {
        String fileName = "test paging.xlsx";
        List<ListObjectSheetTest.Item> expectList = ListObjectSheetTest.Item.randomTestData(1024);
        Workbook workbook = new Workbook()
            .addSheet(new ListSheet<>(expectList))
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Item.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("id", header.get(0));
                assertEquals("name", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    ListObjectSheetTest.Item expect = expectList.get(a++), e = iter.next().get();
                    assertEquals(expect, e);
                }
            }
        }
    }

    @Test public void testLessPaging() throws IOException {
        String fileName = "test less paging.xlsx";
        List<ListObjectSheetTest.Item> expectList = ListObjectSheetTest.Item.randomTestData(23);
        Workbook workbook = new Workbook()
            .addSheet(new ListSheet<>(expectList))
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Item.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("id", header.get(0));
                assertEquals("name", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    ListObjectSheetTest.Item expect = expectList.get(a++), e = iter.next().get();
                    assertEquals(expect, e);
                }
            }
        }
    }

    @Test public void testStringWaterMark() throws IOException {
        String fileName = "paging string water mark.xlsx";
        List<ListObjectSheetTest.Item> expectList = ListObjectSheetTest.Item.randomTestData();
        Workbook workbook = new Workbook()
            .setWaterMark(WaterMark.of("SECRET"))
            .addSheet(new ListSheet<>(expectList))
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Item.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("id", header.get(0));
                assertEquals("name", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    ListObjectSheetTest.Item expect = expectList.get(a++), e = iter.next().get();
                    assertEquals(expect, e);
                }

                List<Drawings.Picture> pictures = sheet.listPictures();
                assertEquals(pictures.size(), 1);
                assertTrue(pictures.get(0).isBackground());
            }
        }
    }

    @Test public void testLocalPicWaterMark() throws IOException {
        String fileName = "paging local pic water mark.xlsx";
        List<ListObjectSheetTest.Item> expectList = ListObjectSheetTest.Item.randomTestData();
        Workbook workbook = new Workbook()
            .setWaterMark(WaterMark.of(testResourceRoot().resolve("mark.png")))
            .addSheet(new ListSheet<>(expectList))
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Item.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("id", header.get(0));
                assertEquals("name", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    ListObjectSheetTest.Item expect = expectList.get(a++), e = iter.next().get();
                    assertEquals(expect, e);
                }

                List<Drawings.Picture> pictures = sheet.listPictures();
                assertEquals(pictures.size(), 1);
                assertTrue(pictures.get(0).isBackground());
            }
        }
    }

    @Test public void testStreamWaterMark() throws IOException {
        String fileName = "paging input stream water mark.xlsx";
        List<ListObjectSheetTest.Item> expectList = ListObjectSheetTest.Item.randomTestData();
        Workbook workbook = new Workbook()
            .setWaterMark(WaterMark.of(getClass().getClassLoader().getResourceAsStream("mark.png")))
            .addSheet(new ListSheet<>(expectList))
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Item.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("id", header.get(0));
                assertEquals("name", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    ListObjectSheetTest.Item expect = expectList.get(a++), e = iter.next().get();
                    assertEquals(expect, e);
                }

                List<Drawings.Picture> pictures = sheet.listPictures();
                assertEquals(pictures.size(), 1);
                assertTrue(pictures.get(0).isBackground());
            }
        }
    }

    @Test public void testPagingCustomizeDataSource() throws IOException {
        String fileName = "paging customize datasource.xlsx";
        List<ListObjectSheetTest.Student> expectList = new ArrayList<>();
        Workbook workbook = new Workbook()
            .setAutoSize(true)
            .addSheet(new CustomizeDataSourceSheet() {
                @Override
                public List<ListObjectSheetTest.Student> more() {
                    List<ListObjectSheetTest.Student> sub = super.more();
                    if (sub != null) expectList.addAll(sub);
                    return sub;
                }
                @Override
                protected Class<?> getTClass() {
                    return ListObjectSheetTest.Student.class;
                }
            })
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row;
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Student.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("姓名", header.get(0));
                assertEquals("成绩", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    ListObjectSheetTest.Student expect = expectList.get(a++), e = iter.next().get();
                    expect.setId(0); // ID not exported
                    assertEquals(expect, e);
                }
            }
        }
    }

    @Test public void testOrderPaging() throws IOException {
        String fileName = "test fracture order paging.xlsx";
        List<CustomColIndexTest.OrderEntry> expectList = CustomColIndexTest.FractureOrderEntry.randomTestData(1024);
        Workbook workbook = new Workbook()
                .addSheet(new ListSheet<>(expectList))
                .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("s2", header.get(0));
                assertEquals("s", header.get(1));
                assertEquals("d", header.get(2));
                assertEquals("date", header.get(3));
                assertEquals("s4", header.get(4));
                assertEquals("s3", header.get(5));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    CustomColIndexTest.FractureOrderEntry expect = (CustomColIndexTest.FractureOrderEntry) expectList.get(a++);
                    Row row = iter.next();
                    assertEquals(expect.getS2(), row.getString(0));
                    assertEquals(expect.getS(), row.getString(1));
                    assertEquals(expect.getD(), row.getDouble(2));
                    assertEquals(expect.getDate().getTime() / 1000, row.getDate(3).getTime() / 1000); // TODO miss milliseconds
                    assertEquals(expect.getS4(), row.getString(4));
                    assertEquals(expect.getS3(), row.getString(5));
                }
            }
        }
    }

    @Test public void testLargeOrderPaging() throws IOException {
        String fileName = "test large order paging.xlsx";
        List<CustomColIndexTest.OrderEntry> expectList = CustomColIndexTest.LargeOrderEntry.randomTestData(1024);
        Workbook workbook = new Workbook()
                .addSheet(new ListSheet<>(expectList))
                .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("s", header.get(1));
                assertEquals("d", header.get(2));
                assertEquals("s3", header.get(4));
                assertEquals("s4", header.get(5));
                assertEquals("s2", header.get(189));
                assertEquals("date", header.get(16_383));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    CustomColIndexTest.LargeOrderEntry expect = (CustomColIndexTest.LargeOrderEntry) expectList.get(a++);
                    Row row = iter.next();
                    assertEquals(expect.getS(), row.getString(1));
                    assertEquals(expect.getD(), row.getDouble(2));
                    assertEquals(expect.getS3(), row.getString(4));
                    assertEquals(expect.getS4(), row.getString(5));
                    assertEquals(expect.getS2(), row.getString(189));
                    assertEquals(expect.getDate().getTime() / 1000, row.getDate(16_383).getTime() / 1000);
                }
            }
        }
    }

    @Test public void testAutoSizePaging() throws IOException {
        String fileName = "test auto-size paging.xlsx";
        List<ListObjectSheetTest.Item> expectList = ListObjectSheetTest.Item.randomTestData(1024);
        Workbook workbook = new Workbook().setAutoSize(true)
            .addSheet(new ListSheet<>(expectList).setStyleProcessor((a, b, c) -> {
                if (a.getId() > 95)
                    b |= c.addFill(new Fill(PatternType.solid, Color.orange));
                return b;
            }))
            .setWorkbookWriter(new ReLimitXMLWorkbookWriter());
        workbook.writeTo(defaultTestPath.resolve(fileName));

        int count = expectList.size(), rowLimit = workbook.getSheetAt(0).getSheetWriter().getRowLimit() - 1; // 1 header row
        try (ExcelReader reader = ExcelReader.read(defaultTestPath.resolve(fileName))) {
            assertEquals(reader.getSheetCount(), (count % rowLimit > 0 ? count / rowLimit + 1 : count / rowLimit));

            for (int i = 0, len = reader.getSheetCount(), a = 0; i < len; i++) {
                Sheet sheet = reader.sheet(i).header(1).bind(ListObjectSheetTest.Item.class);
                org.ttzero.excel.reader.HeaderRow header = (HeaderRow) sheet.getHeader();
                assertEquals("id", header.get(0));
                assertEquals("name", header.get(1));
                Iterator<Row> iter = sheet.iterator();
                while (iter.hasNext()) {
                    Row row = iter.next();
                    ListObjectSheetTest.Item expect = expectList.get(a++), e = row.to(ListObjectSheetTest.Item.class);
                    assertEquals(expect, e);
                    if (expect.getId() > 95) {
                        Styles styles = row.getStyles();
                        Fill fill0 = styles.getFill(row.getCellStyle(0)), fill1 = styles.getFill(row.getCellStyle(1));
                        assertTrue(fill0 != null && fill0.getPatternType() == PatternType.solid && fill0.getFgColor().equals(Color.orange));
                        assertTrue(fill1 != null && fill1.getPatternType() == PatternType.solid && fill1.getFgColor().equals(Color.orange));
                    }
                }
            }
        }
    }
}
