/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package io.netty.buffer.toolkit;

import java.util.LinkedList;
import java.util.List;

/**
 * 一个简单的构造表格的工具
 *
 * @author cuodao
 */
public class TableBuilder {

    private int columns;
    private final List<String[]> rows = new LinkedList<String[]>();
    private boolean customSplitLine = false;

    public TableBuilder() {

    }

    public void addRow(String... cols) {
        if(columns==0){
            columns = cols.length;
        }
        rows.add(cols);
    }

    /**
     * 设置header和分割线, 不鼓励自定义分割线，请使用另一个setHeader方法
     *
     *cols
     *splitStr
     */
    @Deprecated
    public void setHeader(String[] cols, String splitStr) {
        addSplitLine(splitStr);
        addRow(cols);
        addSplitLine(splitStr);
        customSplitLine = true;
    }

    /**
     * 设置header，会自动生成header的分割线
     *
     *cols
     */
    public void setHeader(String[] cols) {
        addRow(cols);
    }

    public void addSplitLine(String str) {
        String[] arr = new String[columns];
        for (int i = 0; i < columns; i++) {
            arr[i] = (str);
        }
        addRow(arr);
    }

    private int[] colWidths(int maxCell) {
        int cols = 0;
        for (String[] row : rows) {
            cols = Math.max(cols, row.length);
        }

        int[] widths = new int[cols];
        for (String[] row : rows) {
            for (int colNum = 0; colNum < row.length; colNum++) {
                widths[colNum] = Math.min(Math.max(widths[colNum], TextUtils.length(row[colNum])), maxCell);
            }
        }
        return widths;
    }

    @Override
    public String toString() {
        return toString(120);
    }

    public String toString(int maxCellWidth) {
        StringBuilder buf = new StringBuilder();
        int[] colWidths = colWidths(maxCellWidth);
        if (colWidths.length <= 0)
            return "";

        if (customSplitLine) {
            for (String[] line : rows) {
                if (line != null && line.length > 0)
                    buf.append(format(transform(line), colWidths));
            }
        } else {
            String[] splitLine = generateSplitLine(colWidths);
            String[] header = rows.get(0);

            // header
            buf.append(format(transform(splitLine), colWidths));
            buf.append(format(transform(header), colWidths));
            buf.append(format(transform(splitLine), colWidths));

            // body
            for (int r = 1; r < rows.size(); r++) {
                String[] line = rows.get(r);
                if (line != null && line.length > 0)
                    buf.append(format(transform(line), colWidths));
            }
        }
        return buf.toString();
    }

    private String[] generateSplitLine(int[] colWidths) {
        int colNum = colWidths.length;
        String[] splitLine = new String[colNum];
        for (int c = 0; c < colNum; c++) {
            int width = colWidths[c];
            splitLine[c] = TextUtils.repeat('-', width);
        }
        return splitLine;
    }

    private String format(String[][] matrix, int[] colWidths) {
        StringBuilder sb = new StringBuilder();
        int lines = matrix.length;
        for (int i = 0; i < lines; i++) { //行
            int cols = matrix[i].length;
            for (int j = 0; j < cols; j++) {//列
                String cell = matrix[i][j];
                int colWidth = colWidths[j];
                //如果超过宽度截断
                if (cell != null && cell.length() > colWidth) {
                    cell = cell.substring(0, colWidth - 3) + "...";
                }
                String formatted = TextUtils.rightPad(TextUtils.defaultString(cell), colWidth);
                sb.append(formatted).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // 支持列字符串内容存在换行的情况，转换为二维数组
    private String[][] transform(String[] cols) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i] == null)
                cols[i] = "";
        }

        String ls = System.lineSeparator();
        int maxLines = 1;
        for (int c = 0; c < cols.length; c++) {
            String col = cols[c];
            if (col.indexOf(ls) == -1)
                continue;

            int n = col.split(ls).length;
            if (n > maxLines)
                maxLines = n;
        }

        String[][] matrix = new String[maxLines][];
        for (int r = 0; r < maxLines; r++) {
            matrix[r] = new String[cols.length];
        }

        for (int c = 0; c < cols.length; c++) {
            String col = cols[c];
            if (col.indexOf(ls) == -1) {
                matrix[0][c] = col;
            } else {
                String[] lines = col.split(ls);
                for (int r = 0; r < lines.length; r++) {
                    matrix[r][c] = lines[r];
                }
            }
        }
        return matrix;
    }
}
