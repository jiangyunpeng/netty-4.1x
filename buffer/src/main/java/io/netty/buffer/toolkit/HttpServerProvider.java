/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package io.netty.buffer.toolkit;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public abstract class HttpServerProvider {

    private static final Object lock = new Object();
    private static HttpServerProvider provider = null;

    protected HttpServerProvider() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(new RuntimePermission("httpServerProvider"));
    }

    private static boolean loadProviderFromProperty() {
        String cn = System.getProperty("com.sun.net.httpserver.HttpServerProvider");
        if (cn == null)
            return false;
        try {
            Class<?> c = Class.forName(cn, true, ClassLoader.getSystemClassLoader());
            provider = (HttpServerProvider) c.newInstance();
            return true;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | SecurityException x) {
            throw new ServiceConfigurationError(null, x);
        }
    }

    private static boolean loadProviderAsService() {
        Iterator<HttpServerProvider> i = ServiceLoader.load(HttpServerProvider.class,
            ClassLoader.getSystemClassLoader()).iterator();
        for (;;) {
            try {
                if (!i.hasNext())
                    return false;
                provider = i.next();
                return true;
            } catch (ServiceConfigurationError sce) {
                if (sce.getCause() instanceof SecurityException) {
                    // Ignore the security exception, try the next provider
                    continue;
                }
                throw sce;
            }
        }
    }

    public static HttpServerProvider provider() {
        synchronized (lock) {
            if (provider != null)
                return provider;
            return (HttpServerProvider) AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    if (loadProviderFromProperty())
                        return provider;
                    if (loadProviderAsService())
                        return provider;
                    return null;
                }
            });
        }
    }

}
