// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.esb.runcontainer.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.eclipse.jface.preference.IPreferenceStore;
import org.talend.designer.esb.runcontainer.core.ESBRunContainerPlugin;
import org.talend.designer.esb.runcontainer.preferences.RunContainerPreferenceInitializer;

/**
 * DOC yyan class global comment. Detailled comment <br/>
 * Utils for JMX operation
 */
public class JMXUtil {

    private static final String CREDENTIALS = "jmx.remote.credentials";

    public static String username;

    public static String password;

    public static String host;

    public static String jmxPort;

    public static String karafPort;

    public static String instanceName;

    public static String serviceUrl;

    private static MBeanServerConnection mbsc;

    private static JMXConnector jmxc;

    private static int connectNumber = 0;

    static {
        reloadPreference();
    }

    /**
     * for Job installation
     * 
     * @param bundle
     * @return
     * @throws ReflectionException
     */
    public static long[] installBundle(File bundle) throws Exception {

        try {

            // MBeanServerConnection mbsc = createJMXconnection();
            String KARAF_BUNDLE_MBEAN = "org.apache.karaf:type=bundle,name=trun";
            ObjectName objectBundle = new ObjectName(KARAF_BUNDLE_MBEAN);

            Set<Object> existsBundles = ((TabularDataSupport) mbsc.getAttribute(objectBundle, "Bundles")).keySet();

            Object bundleId = mbsc.invoke(objectBundle, "install", new Object[] { "file:" + bundle.getAbsolutePath() },
                    new String[] { String.class.getName() });

            Set<Object> newBundles = ((TabularDataSupport) mbsc.getAttribute(objectBundle, "Bundles")).keySet();
            newBundles.removeAll(existsBundles);
            Object[] bundleIds = newBundles.toArray();
            long[] newIds = new long[bundleIds.length];
            for (int i = 0; i < bundleIds.length; i++) {
                String id = bundleIds[i].toString();
                newIds[i] = Long.parseLong(id.substring(1, id.length() - 1));
                mbsc.invoke(objectBundle, "start", new Object[] { String.valueOf(newIds[i]) },
                        new String[] { String.class.getName() });
            }

            return newIds; // bundleId instanceof Long ? (long) bundleId : 0;
        } finally {
            // closeJMXConnection();
        }
    }

    public static void uninstallBundle(long bundleID) throws Exception {

        try {

            // MBeanServerConnection mbsc = createJMXconnection();
            String KARAF_BUNDLE_MBEAN = "org.apache.karaf:type=bundle,name=trun";
            ObjectName objectBundle = new ObjectName(KARAF_BUNDLE_MBEAN);
            Object bundleId = mbsc.invoke(objectBundle, "uninstall", new Object[] { String.valueOf(bundleID) },
                    new String[] { String.class.getName() });
        } finally {
            // closeJMXConnection();
        }
    }

    /**
     * for Route installation
     * 
     * @param kar
     * @throws ReflectionException
     */
    public static String[] installKar(File kar) throws Exception {

        // MBeanServerConnection mbsc = createJMXconnection();
        String KARAF_KAR_MBEAN = "org.apache.karaf:type=kar,name=trun";

        ObjectName objectKar = new ObjectName(KARAF_KAR_MBEAN);

        ArrayList existKars = (ArrayList) mbsc.getAttribute(objectKar, "Kars");

        mbsc.invoke(objectKar, "install", new Object[] { "file:" + kar.getAbsolutePath().replaceAll("\\\\", "/") },
                new String[] { String.class.getName() });

        ArrayList kars = (ArrayList) mbsc.getAttribute(objectKar, "Kars");
        kars.removeAll(existKars);

        String[] addedKars = new String[kars.size()];
        for (int i = 0; i < kars.size(); i++) {
            addedKars[i] = String.valueOf(kars.get(i));
        }
        return addedKars;
    }

    public static void uninstallKar(String karID) throws Exception {

        // MBeanServerConnection mbsc = createJMXconnection();
        String KARAF_KAR_MBEAN = "org.apache.karaf:type=kar,name=trun";

        ObjectName objectKar = new ObjectName(KARAF_KAR_MBEAN);

        mbsc.invoke(objectKar, "uninstall", new Object[] { karID }, new String[] { String.class.getName() });

    }

    public static String getSystemPropertie(String name) throws Exception {

        // MBeanServerConnection mbsc = createJMXconnection();
        String COMMAND_MBEAN = "com.sun.management:type=DiagnosticCommand";

        ObjectName objectCommand;
        objectCommand = new ObjectName(COMMAND_MBEAN);
        String properties = String.valueOf(mbsc.invoke(objectCommand, "vmSystemProperties", new Object[] {}, new String[] {}));
        int homeValueBegin = properties.indexOf(name) + name.length() + 1;
        int homeValueEnd = properties.indexOf('\n', homeValueBegin) - 1;
        return properties.substring(homeValueBegin, homeValueEnd);
    }

    public static long findBundleIDWithKarName(String karName) {

        return 0;
    }

    /**
     * if use catched connection
     * 
     * @return MBeanServerConnection
     */
    public static MBeanServerConnection createJMXconnection() {
        if (!isConnected()) {

            reloadPreference();
            try {
                jmxc = null;
                Map<String, String[]> env = new HashMap<String, String[]>();
                env.put(CREDENTIALS, new String[] { username, password });
                jmxc = JMXConnectorFactory.connect(new JMXServiceURL(serviceUrl), env);

                mbsc = null;
                mbsc = jmxc.getMBeanServerConnection();
            } catch (IOException ex) {
                // ex.printStackTrace();
                jmxc = null;
                mbsc = null;
            }
        }
        connectNumber++;
        return mbsc;
    }

    /**
     * Reload perference
     */
    private static void reloadPreference() {
        IPreferenceStore store = ESBRunContainerPlugin.getDefault().getPreferenceStore();
        if (store != null) {
            username = store.getString(RunContainerPreferenceInitializer.P_ESB_RUNTIME_USERNAME);

            password = store.getString(RunContainerPreferenceInitializer.P_ESB_RUNTIME_PASSWORD);

            host = store.getString(RunContainerPreferenceInitializer.P_ESB_RUNTIME_HOST);

            jmxPort = store.getString(RunContainerPreferenceInitializer.P_ESB_RUNTIME_JMX_PORT);

            karafPort = store.getString(RunContainerPreferenceInitializer.P_ESB_RUNTIME_PORT);

            instanceName = store.getString(RunContainerPreferenceInitializer.P_ESB_RUNTIME_INSTANCE);

            serviceUrl = "service:jmx:rmi://" + host + ":" + jmxPort + "/jndi/rmi://" + host + ":" + karafPort + "/karaf-"
                    + instanceName;
        }
    }

    /**
     * Check whether the JMX connection is connected
     * 
     * @return
     */
    public static boolean isConnected() {
        if (mbsc == null || jmxc == null) {
            return false;
        }
        try {
            jmxc.getConnectionId();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static void closeJMXConnection() {
        if (jmxc != null/* && connectNumber < 1 */) {
            try {
                jmxc.close();
                jmxc = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        connectNumber--;
    }

    public static void halt() throws Exception {
        // need to re connect
        MBeanServerConnection mbsc = createJMXconnection();
        String SYS_MBEAN = "org.apache.karaf:type=system,name=trun";
        ObjectName objectKar = new ObjectName(SYS_MBEAN);
        mbsc.invoke(objectKar, "halt", new Object[] {}, new String[] {});
    }

}