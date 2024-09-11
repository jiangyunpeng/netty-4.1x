/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package io.netty.buffer.toolkit;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class SystemToolkit {

    public static final String OS_NAME = System.getProperty("os.name");

    public static final String OS_INFO = getOsInfo();

    public static final boolean IS_LINUX = isLinux();

    public static final String JAVA_RUNTIME_VERSION = getJavaRuntimeVersion();

    public static final float JAVA_VERSION = getJavaVersion();

    public static final long JVM_START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();

    public static final long JVM_INIT_HEAP = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit();

    public static final long JVM_MAX_HEAP = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();


    public static int getPID() {
        // 8368@whj-desktop -> 8386
        String rtName = ManagementFactory.getRuntimeMXBean().getName();
        int index = rtName.indexOf('@');
        if (index != -1)
            return Integer.parseInt(rtName.substring(0, index));
        return -99;
    }


    public static void printMemInfo(PrintWriter pw) {
        Runtime rt = Runtime.getRuntime();
        pw.println("Total Memory: " + rt.totalMemory() / 1024 / 1024 + "m");
        pw.println("Max Memory:   " + rt.maxMemory() / 1024 / 1024 + "m");
        pw.println("Free Memory:  " + rt.freeMemory() / 1024 / 1024 + "m");
    }


    public static void printThreadInfo(PrintWriter pw) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        pw.println("Peak Thread Count:    " + threadMXBean.getPeakThreadCount());
        pw.println("Current Thread Count: " + threadMXBean.getThreadCount());
    }

    public static List<InetAddress> getAllAddresses() {
        try {
            List<InetAddress> list = new ArrayList<InetAddress>();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                for (InetAddress addr : Collections.list(netint.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        list.add(addr);
                    }
                }
            }
            return list;
        } catch (SocketException e) {
            return new ArrayList<InetAddress>(0);
        }
    }

    public static InetAddress getFirstNonLoopAddress() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                for (InetAddress addr : Collections.list(netint.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
                        return addr;
                }
            }
        } catch (SocketException e) {
        }
        return null;
    }

    public static boolean isPortAvailable(int port) {
        // MIN_PORT_NUMBER:0 MAX_PORT_NUMBER:65535
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid start port: " + port);

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
        } finally {
            if (ds != null)
                ds.close();
            if (ss != null)
                try {
                    ss.close();
                } catch (IOException e) {
                }
        }
        return false;
    }

    public static String getSystemProperty(String key) {
        String value = System.getenv(key);
        if (value == null || value.length() == 0) {
            value = System.getProperty(key);
        }
        return value;
    }


    public static InetAddress parseIP(String ipStr) throws UnknownHostException {
        if (ipStr == null || ipStr.isEmpty())
            throw new UnknownHostException("ip address cannot be null or empty");

        byte[] ip = new byte[4];
        String[] sa = ipStr.split("[.]");
        for (int i = 0; i < 4; i++) {
            ip[i] = (byte) Integer.parseInt(sa[i]);
        }
        return InetAddress.getByAddress(ip);
    }

    private static final boolean isLinux() {
        if (OS_NAME != null)
            return OS_NAME.toUpperCase().startsWith("LINUX");
        String os = System.getProperty("os.name");
        if (os != null)
            return os.toUpperCase().startsWith("LINUX");
        return false;
    }

    private static final String getJavaRuntimeVersion() {
        // eg: 1.8.0_51-b16
        return System.getProperty("java.runtime.version");
    }

    private static final float getJavaVersion() {
        String str = System.getProperty("java.specification.version"); //eg: 1.8
        return Float.parseFloat(str);
    }


    private static final String getOsInfo() {
        // eg: Mac OS X 10.12.1 / Linux 2.6.32-573.3.1.el6.x86_64
        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }



}

