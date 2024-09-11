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
import com.sun.net.httpserver.HttpServer;
import io.netty.util.SourceLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpEndpoint {

    public static final int DEFAULT_PORT = 8864;
    public int endpointPort;

    private static final Map<String, Set<String>> urlMap = new HashMap<String, Set<String>>();
    private static HttpEndpoint INSTANCE;

    public static HttpEndpoint getInstance() {
        return getInstance(DEFAULT_PORT);
    }

    public synchronized static HttpEndpoint getInstance(int port) {
        if (INSTANCE == null) {
            INSTANCE = new HttpEndpoint(port);
        }
        return INSTANCE;
    }

    private final HttpServer httpServer;
    private volatile boolean started;

    private HttpEndpoint(int port) {
        this.endpointPort = port;
        this.httpServer = buildServer();
        startServerAsDaemon();
        registerDefaultActions();
    }

    private void registerDefaultActions() {
        HttpHandler defaultHandler = new HelpAction();
        httpServer.createContext("/", defaultHandler);
    }

    private void startServerAsDaemon() {
        if (Thread.currentThread().isDaemon()) {
            startServer();
            return;
        }

        // sun.net.httpserver.ServerImpl 内部会启动线程，这里必须用daemon线程启动它
        try {
            Thread t = new Thread() {
                public void run() {
                    startServer();
                }
            };
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startServer() {
        try {
            this.httpServer.start();
            this.started = true;
            registerShutdownHook();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpServer buildServer() {
        HttpServer server = null;
        try {
            server = bind();
            DaemonThreadFactory factory = new DaemonThreadFactory("middleware-endpoint");
            Executor executor = new ThreadPoolExecutor(1, 2, 1, TimeUnit.MINUTES,
                new LinkedBlockingQueue<Runnable>(100), factory, new ThreadPoolExecutor.CallerRunsPolicy());
            server.setExecutor(executor);
            SourceLogger.info(this.getClass(),"build HttpServer success");
        } catch (Exception e) {
            SourceLogger.info(this.getClass(),"build HttpServer failed! Cause by {}",e.getMessage());
            throw new RuntimeException(e);
        }
        return server;
    }

    private HttpServer bind() throws IOException {
        int port = endpointPort;
        // if backlog <= 0, then system default value is used
        int backlog = 0;
        HttpServer server = null;
        BindException bindErr = null;
        for (int i = 0; i < 100; i++) {
            // 确保端口可用，测试环境下出现过一个java进程同时绑定了8864个8865两个端口的情况，第一个端口是非工作状态
            if (!SystemToolkit.isPortAvailable(port)) {
                port++;
                continue;
            }
            try {
                InetSocketAddress addr = new InetSocketAddress(port);
                server = HttpServer.create(addr, backlog);
                bindErr = null;
                break;
            } catch (BindException e) {
                bindErr = e; // 端口冲突
                port++;
            }
        }
        if (bindErr != null)
            throw bindErr;
        endpointPort = port;
        return server;
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                if (httpServer == null)
                    return;
                try {
                    if (started) {
                        httpServer.stop(0);
                    }
                } catch (Throwable t) {
                }
            }
        });
    }

    public synchronized void registerAction(Action action) {
        if (action == null)
            return;
        String artifact = action.getArtifact();
        String path = action.getPath();
        if (urlMap.containsKey(artifact)) {
            urlMap.get(artifact).add(path);
        } else {
            Set<String> set = new HashSet<String>();
            set.add(path);
            urlMap.put(artifact, set);
        }
        httpServer.createContext(path, action);
    }

    private static class HelpAction implements HttpHandler {
        public void handle(HttpExchange he) throws IOException {
            TableBuilder tb = new TableBuilder();
            String[] header = {"Module", "Url"};
            tb.setHeader(header);
            for (Map.Entry<String, Set<String>> e : urlMap.entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (String url : e.getValue()) {
                    sb.append(url).append(System.lineSeparator());
                }
                tb.addRow(e.getKey(), sb.toString());
            }

            String res = tb.toString();
            he.sendResponseHeaders(200, res.length());
            OutputStream os = he.getResponseBody();
            os.write(res.getBytes());
            os.close();
        }
    }
}
