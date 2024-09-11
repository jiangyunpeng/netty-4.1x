/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package io.netty.buffer.toolkit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public abstract class Action implements HttpHandler {
    private String artifact;
    private String path;

    public Action(String artifact, String path) {
        this.artifact = artifact;
        this.path = path;
    }

    /**
     * 执行逻辑，返回字符串结果，当参数不满足是抛出异常而非返回空
     *
     */
    abstract public String execute(Map<String, String> params) throws IllegalArgumentException;

    public String getArtifact() {
        return artifact;
    }

    public String getPath() {
        return path;
    }

    public void handle(HttpExchange he) {
        try {
            String res = execute(parseParams(he));
            if (res == null)
                res = "";
            response(he, res);
        } catch (IllegalArgumentException ie) {
            ie.printStackTrace();
            if (!"HEAD".equalsIgnoreCase(he.getRequestMethod())) {
                responseBadRequest(he, ie.getMessage() + "\r\n");
            }
        }
    }

    protected void response(HttpExchange he, String msg) {
        OutputStream os = null;
        try {
            byte[] ba = msg.getBytes();
            he.sendResponseHeaders(200, ba.length);
            if ("HEAD".equalsIgnoreCase(he.getRequestMethod()))
                return;

            os = he.getResponseBody();
            os.write(msg.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                }
            }
        }
    }

    protected void responseBadRequest(HttpExchange he, String msg) {
        OutputStream os = null;
        try {
            byte[] ba = (msg == null || msg.isEmpty()) ? "Illegal parameter.\r\n".getBytes() : msg.getBytes();
            he.sendResponseHeaders(400, ba.length);
            os = he.getResponseBody();
            os.write(ba);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                }
            }
        }
    }

    protected Map<String, String> parseParams(HttpExchange he) {
        String queryStr = he.getRequestURI().getQuery();
        if (queryStr == null || queryStr.isEmpty())
            return new HashMap<String, String>(0);
        return query2Map(queryStr);
    }

    private Map<String, String> query2Map(String query) {
        Map<String, String> map = new HashMap<String, String>();
        for (String param : query.split("[&]")) {
            if (param == null || param.isEmpty())
                continue;

            // 注意参数值内容可能包含"="
            // http://localhost:8864/jmx-proxy?get=java.lang:type=Memory&att=HeapMemoryUsage&key=used
            int i = param.indexOf('=');
            if (i == -1)
                continue;
            String key = param.substring(0, i);
            String value = null;
            if (param.length() > i)
                value = param.substring(i + 1);
            map.put(key, value);
        }
        return map;
    }
}
