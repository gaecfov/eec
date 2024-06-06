/*
 * Copyright (c) 2017-2018, guanquan.wang@yandex.com All Rights Reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ttzero.excel.entity.e7.ContentType;
import org.ttzero.excel.manager.Const;
import org.ttzero.excel.util.FileUtil;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.ttzero.excel.util.FileUtil.exists;

/**
 * @author guanquan.wang at 2018-02-26 13:45
 * @deprecated 使用 {@link TemplateSheet}代替
 */
@Deprecated
public abstract class AbstractTemplate {
    /**
     * LOGGER
     */
    protected Logger LOGGER = LoggerFactory.getLogger(getClass());
    static final String inlineStr = "inlineStr";
    protected Workbook wb;

    protected Path zipPath;
    protected Map<String, String> map;

    public AbstractTemplate(Path zipPath, Workbook wb) {
        this.zipPath = zipPath;
        this.wb = wb;
    }

    /**
     * The open-xml legitimate check
     *
     * @return true if legitimate
     */
    public boolean check() {
        // Integrity check
        Path contentTypePath = zipPath.resolve("[Content_Types].xml");
        SAXReader reader = SAXReader.createDefault();
        Document document;
        try {
            document = reader.read(Files.newInputStream(contentTypePath));
        } catch (DocumentException | IOException e) {
            LOGGER.debug("{} loading error", "[Content_Types].xml");
            return false;
        }

        List<ContentType.Override> overrides = new ArrayList<>();
        List<ContentType.Default> defaults = new ArrayList<>();
        Iterator<Element> it = document.getRootElement().elementIterator();
        while (it.hasNext()) {
            Element e = it.next();
            if ("Override".equals(e.getName())) {
                overrides.add(new ContentType.Override(e.attributeValue("ContentType"), e.attributeValue("PartName")));
            } else if ("Default".equals(e.getName())) {
                defaults.add(new ContentType.Default(e.attributeValue("ContentType"), e.attributeValue("Extension")));
            }
        }

        return checkDefault(defaults) && checkOverride(overrides);
    }

    protected boolean checkDefault(List<ContentType.Default> list) {
        // Double check
        if (list.isEmpty() || !checkDouble(list)) {
            LOGGER.debug("The attribute[Default] is empty or the same attribute exists");
        }
        return true;
    }

    protected boolean checkOverride(List<ContentType.Override> list) {
        // Double check
        if (list.isEmpty() || !checkDouble(list)) {
            LOGGER.debug("The attribute[Override] is empty or the same attribute exists");
        }
        // File exists check
        for (ContentType.Override o : list) {
            Path subPath = zipPath.resolve(o.getPartName().substring(1));
            if (!exists(subPath)) {
                LOGGER.debug("{} does not exists", subPath.toString());
                return false;
            }
        }

        return true;
    }

    private boolean checkDouble(List<? extends ContentType.Type> list) {
        list.sort(Comparator.comparing(ContentType.Type::getKey));
        int i = 0, len = list.size() - 1;
        boolean boo = false;
        for (; i < len; i++) {
            if (boo = list.get(i).getKey().equals(list.get(i + 1).getKey()))
                break;
        }
        return !(i < len || boo);
    }

    /**
     * Replace the placeholder character with Entry
     *
     * @param o the entry
     */
    public void bind(Object o) {
        if (o != null) {
            // Translate object to string hashMap
            map = new HashMap<>();
            if (o instanceof Map) {
                Map<?, ?> map1 = (Map<?, ?>) o;
                map1.forEach((k, v) -> {
                    String vs = v != null ? v.toString() : "";
                    map.put(k.toString(), vs);
                });
            } else {
                Field[] fields = o.getClass().getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    String value;
                    try {
                        Object v = field.get(o);
                        if (v != null) {
                            value = v.toString();
                        } else value = "";
                    } catch (IllegalAccessException e) {
                        value = "";
                    }
                    map.put(field.getName(), value);
                }
            }
        }
        // Search SharedStrings
        int n1 = bindSstData();
        // inner text
        int n2 = bindSheetData();

        LOGGER.debug("Found {} words that need to be replaced", n1 + n2);
    }

    protected int bindSstData() {
        Path shareStringPath = zipPath.resolve("xl/sharedStrings.xml");
        SAXReader reader = SAXReader.createDefault();
        Document document;
        try {
            document = reader.read(Files.newInputStream(shareStringPath));
        } catch (DocumentException | IOException e) {
            // read style file fail.
            LOGGER.debug("The file format is incorrect or corrupted. [shareStrings.xml]");
            return 0;
        }

        Element sst = document.getRootElement();
        Attribute countAttr = sst.attribute("count");
        // Empty string
        if (countAttr == null || "0".equals(countAttr.getValue())) {
            return 0;
        }
        int n = 0;
        Iterator<Element> iterator = sst.elementIterator();
        while (iterator.hasNext()) {
            Element si = iterator.next(), t = si.element("t");
            String txt = t.getText();
            // Determine whether it is a placeholder text
            if (isPlaceholder(txt)) {
                // Replace if it is a placeholder text
                t.setText(getValue(txt));
                n++;
            }
        }

        if (n > 0) {
            try {
                FileUtil.writeToDiskNoFormat(document, shareStringPath);
            } catch (IOException e) {
                LOGGER.debug("Write {} failed.", shareStringPath.toString());
                // Do nothing
            }
        }
        return n;
    }

    protected int bindSheetData() {
        // Read content
        Path contentTypePath = zipPath.resolve("[Content_Types].xml");
        SAXReader reader = SAXReader.createDefault();
        Document document;
        try {
            document = reader.read(Files.newInputStream(contentTypePath));
        } catch (DocumentException | IOException e) {
            // read style file fail.
            LOGGER.debug("The file format is incorrect or corrupted. [[Content_Types].xml]");
            return 0;
        }

        // Find Override
        List<Element> overrides = document.getRootElement().elements("Override");
        int[] result = overrides.stream()
            .filter(e -> Const.ContentType.SHEET.equals(e.attributeValue("ContentType")))
            .map(e -> zipPath.resolve(e.attributeValue("PartName").substring(1)))
            .mapToInt(this::bindSheet)
            .toArray();

        int n = 0;
        for (int i : result) n += i;
        return n;
    }

    int bindSheet(Path sheetPath) {
        SAXReader reader = SAXReader.createDefault();
        Document document;
        try {
            document = reader.read(Files.newInputStream(sheetPath));
        } catch (DocumentException | IOException e) {
            // read style file fail.
            LOGGER.debug("The file format is incorrect or corrupted. [{}]", sheetPath);
            return 0;
        }

        int n = 0;
        Element sheetData = document.getRootElement().element("sheetData");
        // Each rows
        Iterator<Element> iterator = sheetData.elementIterator();
        while (iterator.hasNext()) {
            Element row = iterator.next();
            // Each cells
            Iterator<Element> it = row.elementIterator();
            while (it.hasNext()) {
                Element cell = it.next();
                Attribute t = cell.attribute("t");
                if (t != null && inlineStr.equals(t.getValue())) {
                    Element tv = cell.element("is").element("t");
                    String txt = tv.getText();
                    // Determine whether it is a placeholder text
                    if (isPlaceholder(txt)) {
                        // Replace if it is a placeholder text
                        tv.setText(getValue(txt));
                        n++;
                    }
                }
            }
        }

        if (n > 0) {
            try {
                FileUtil.writeToDiskNoFormat(document, sheetPath);
            } catch (IOException e) {
                LOGGER.debug("Compression completed. {}", sheetData);
                // Do nothing
            }
        }

        return n;
    }

    ////////////////////////////Abstract function/////////////////////////////

    /**
     * Check the cell content has placeholder character
     *
     * @param txt the content of cell
     * @return true if the content has placeholder character
     */
    protected abstract boolean isPlaceholder(String txt);

    /**
     * Returns the placeholder character
     *
     * @param txt the content of cell
     * @return the placeholder character
     */
    protected abstract String getValue(String txt);
}
