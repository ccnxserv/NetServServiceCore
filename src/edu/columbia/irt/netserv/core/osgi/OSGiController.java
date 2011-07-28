package edu.columbia.irt.netserv.core.osgi;

import edu.columbia.irt.netserv.core.backbone.Service;
import edu.columbia.irt.netserv.core.backbone.ServiceException;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import org.osgi.framework.launch.Framework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

import edu.columbia.irt.netserv.core.backbone.ServiceEvent;
import edu.columbia.irt.netserv.core.backbone.ServiceListener;
import edu.columbia.irt.netserv.core.backbone.Util;
import java.lang.reflect.Method;
import org.osgi.framework.Constants;

public class OSGiController implements Runnable {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller");

    static {
        Util.setupConsoleLogging(logger, Level.INFO);
    }
    public static final HashSet<ServiceListener> serviceListeners =
            new HashSet<ServiceListener>();
    public static final HashMap<Long, Properties> mapServiceProps =
            new HashMap<Long, Properties>();
    // for EXECUTE command - keeps track of installed service
    static final HashMap<String, Bundle> serviceMap =
            new HashMap<String, Bundle>();
    // OSGi Equinox Framework instance
    private Framework framework;
    // control port 
    private int ctrlPort;
    private HashMap<Long, String> filterMap = new HashMap<Long, String>();
    private HashMap<Long, TimerTask> expiryMap = new HashMap<Long, TimerTask>();
    private Timer timer = new Timer("expiry timer", true);

    public OSGiController(Framework framework, int ctrlPort) {
        this.framework = framework;
        this.ctrlPort = ctrlPort;
    }

    public void start() {
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            logger.log(Level.INFO, "Starting Netserv OSGi Controller Thread");
            ServerSocket servSock = new ServerSocket();
            servSock.setReuseAddress(true);
            servSock.bind(new InetSocketAddress(ctrlPort), 1);

            for (;;) {
                Socket sock = servSock.accept();
                handleConnection(sock);
                sock.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error starting NetServ OSGi Controller Thread {0}",
                    e.toString());
        }
    }

    /*
     * Takes commands of the following format (whitespace-forgiving):
     *
     * 		COMMAND [argument] [comments ...]\n
     * 		key: value\n
     * 		key: value\n
     * 		...
     * 		\n
     *
     * and returns:
     *
     * 		<code>\n  # 0 on success, negative on failure
     * 		[result ...]\n
     * 		[result ...]\n
     * 		...
     * 		\n
     */
    void handleConnection(Socket sock) throws IOException {
        // socket input
        BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream()));
        // socket output
        PrintWriter out = new PrintWriter(
                sock.getOutputStream(), true);

        for (;;) {

            String command = "";
            String arg = "";
            String comments = "";
            HashMap<String, String> m = new HashMap<String, String>();

            // get the request line
            String line = in.readLine();
            if (line == null) {
                logger.log(Level.INFO, "Socket connection closed");
                return;
            }
            line = line.trim();
            // split in 3 parts, separated by whitespaces
            String a[] = line.split("\\s+", 3);
            if (a.length >= 1) {
                command = a[0].toUpperCase(Locale.US);
                if (a.length >= 2) {
                    arg = a[1];
                    if (a.length >= 3) {
                        comments = a[2];
                    }
                }
            }

            // get the headers
            while ((line = in.readLine().trim()).length() > 0) {
                a = line.split(":", 2);
                if (a.length == 2) {
                    m.put(a[0].trim().toLowerCase(Locale.US), a[1].trim());
                }
            }

            logger.log(Level.INFO, "handleConnection() - command received: {0} {1} {2}",
                    new Object[]{command, arg, comments});
            logger.log(Level.INFO, "headers: {0}", m);

            try {
                if (command.equals("EXECUTE")) {
                    // check if the service is already installed.
                    Bundle service = serviceMap.get(arg);
                    if (service.getState() == Bundle.ACTIVE) {
                        try {
                            logger.log(Level.INFO, "Bundle {0} found in ACTIVE state", arg);
                            executeModule(service, m, sock.getOutputStream());
                            out.printf("\n");
                        } catch (ClassNotFoundException ex) {
                            out.printf("Service class not found :  %s\n\n", ex.toString());
                        }
                    }

                } else if (command.equals("SETUP")) {
                    int ttl = 5 * 60;
                    String ttlStr = m.get("ttl");
                    if (ttlStr != null) {
                        ttl = Integer.parseInt(ttlStr);
                    }
                    setupModule(arg, m.get("url"), ttl, m);
                    out.printf("%d\n\n", 0); // success

                } else if (command.equals("REMOVE")) {
                    removeModule(arg);
                    out.printf("%d\n\n", 0); // success

                } else if (command.equals("UPDATE")) {
                    // TODO
                } else if (command.equals("STATUS")) {
                    StringBuilder s = getModuleStatus(arg);
                    out.printf("%d\n", 0); // success
                    out.append(s);
                    out.printf("\n");

                } else {
                    out.printf("%d\n", -1); // failure
                    out.printf("%s: unknown command\n\n", command);
                }
            } catch (BundleException ex) {
                out.printf("%d\n", -1); // failure
                logger.log(Level.SEVERE, "Bundle exception:", ex);
                out.printf("BundleException: type=%d\n\n", ex.getType());
            }
        }
    }

    synchronized void executeModule(Bundle bundle, HashMap<String, String> headers,
            OutputStream out)
            throws ClassNotFoundException {

        String activator = (String) bundle.getHeaders().get(Constants.BUNDLE_ACTIVATOR);
        Class activatorClass = bundle.loadClass(activator);
        try {
            //Service serviceObject;
            Object serviceObject = activatorClass.newInstance();
            Object param = null;
            if (headers.get("args") != null) {
                param = headers.get("args");
            }
            Method method = activatorClass.getMethod("execute", new Class[]{OutputStream.class, Object.class});
            method.invoke(serviceObject, new Object[]{out, param});

        } catch (InvocationTargetException ex) {
            Logger.getLogger(OSGiController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(OSGiController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Fetch a bundle from URL, install and start it, and schedule a
     * removal in TTL seconds.  If the bundle is already there, simply
     * refresh the TTL.
     * 
     * @param moduleID
     * @param url
     * @param ttl
     * @param headers
     * @throws BundleException 
     */
    synchronized void setupModule(String moduleID, String url, int ttl,
            HashMap<String, String> headers) throws BundleException {

        logger.info("Inside setupModule method");
        if (url == null || url.length() == 0) {
            throw new BundleException("URL is missing");
        }
        BundleContext context = framework.getBundleContext();
        Bundle bundle = context.installBundle(url);
        Properties props = new Properties();
        String propStr = headers.get("properties");
        if (propStr != null) {
            String[] a = propStr.split(",", 0);
            for (String s : a) {
                String[] kv = s.split("=", 2);
                String key = "";
                String val = "";
                if (kv.length > 0) {
                    key = kv[0].trim();
                    if (kv.length > 1) {
                        val = kv[1].trim();
                    }
                }
                if (key.length() > 0 && val.length() > 0) {
                    props.setProperty(key, val);
                }
            }
        }
        if (props.size() > 0) {
            synchronized (mapServiceProps) {
                mapServiceProps.put(bundle.getBundleId(), props);
            }
        }

        bundle.start();
        serviceMap.put(moduleID, bundle);

        logger.log(Level.INFO, "{0} bundle installed and started..", moduleID);
        bundle.getState();

        // check installed bundled ID and module ID passed
        String name = bundle.getSymbolicName();
        Version version = bundle.getVersion();
        String realID = name + '_' + version;
        if (moduleID.equals(realID)) {
            logger.log(Level.INFO, "{0} installed", realID);
        } else {
            logger.log(Level.WARNING, "{0} installed (not {1})",
                    new Object[]{realID, moduleID});
        }

        // schedule the bundle's removal in TTL seconds
        final long bid = bundle.getBundleId();
        TimerTask task = expiryMap.get(bid);
        if (task != null) {
            // bundle already present
            task.cancel();
        }
        // make a new TimerTask and schedule it
        task = new TimerTask() {

            @Override
            public void run() {
                try {
                    removeModule(framework.getBundleContext().getBundle(bid));
                } catch (BundleException ex) {
                    logger.log(Level.SEVERE, "Error in updating TTL of bundle");
                }
            }
        };

        expiryMap.put(bid, task);
        timer.schedule(task, 1000L * ttl);
        logger.log(Level.INFO, "{0} will be removed in {1} secs", new Object[]{realID, ttl});

        // install the module's packet filter, if specified in the headers.
        String filter = filterMap.get(bid);
        if (filter != null) {
            logger.log(Level.INFO, "{0}''s filter already present: {1}",
                    new Object[]{realID, filter});
            return;
        }

        filter = "";
        String f_ipv4 = headers.get("filter-ipv4");
        String f_proto = headers.get("filter-proto");
        String f_port = headers.get("filter-port");

        if (f_ipv4 != null) {
            filter += " --dst " + f_ipv4;
        }
        if (f_proto != null) {
            filter += " -p " + f_proto;
            if (f_port != null) {
                filter += " --dport " + f_port;
            }
        }

        if (filter.length() > 0) {
            filter += " -j NFQUEUE --queue-num " + headers.get("filter-queue-num");
            try {
                StringBuilder out = new StringBuilder();
                int res = Util.executeProcess(out,
                        "iptables -t mangle -A PREROUTING " + filter);
                if (res == 0) {
                    filterMap.put(bid, filter);
                    logger.log(Level.INFO, "{0}''s filter successfully installed: {1}", new Object[]{realID, filter});
                } else {
                    logger.log(Level.INFO, "iptables failed: {0}", out);
                }
            } catch (IOException ex) {
                logger.info("iptables threw IOException");
            }
        }

        // notify all module listeners
        String[] moduleAdded = new String[]{realID};
        String[] moduleRemoved = new String[0];
        String[] currentModules = getModulesForState(Bundle.ACTIVE).toArray(new String[0]);
        synchronized (serviceListeners) {
            for (ServiceListener l : serviceListeners) {
                l.serviceChanged(new ServiceEvent(
                        moduleAdded,
                        moduleRemoved,
                        currentModules));
            }
        }
    }

    /**
     * moduleID is name[_version], where it will match any version if
     * version is omitted.
     * 
     * @param moduleID
     * @throws BundleException 
     */
    synchronized void removeModule(String moduleID) throws BundleException {
        if (moduleID == null || moduleID.length() == 0) {
            return;
        }

        String[] a = moduleID.split("_", 2);
        String name = a[0];
        String version = null;
        if (a.length > 1) {
            version = a[1];
        }

        BundleContext context = framework.getBundleContext();
        Bundle[] bundles = context.getBundles();
        for (Bundle b : bundles) {
            if (name.equals(b.getSymbolicName())) {
                if (version == null || version.equals(b.getVersion().toString())) {
                    if (b.getState() != Bundle.UNINSTALLED) {
                        removeModule(b);
                        serviceMap.remove(moduleID);
                        return;
                    }
                }
            }
        }

        logger.log(Level.INFO, "{0} not found", moduleID);
    }

    synchronized void removeModule(Bundle b) throws BundleException {
        if (b == null) {
            return;
        }

        // uninstall the bundle
        final long bid = b.getBundleId();
        b.uninstall();
        String realID = b.getSymbolicName() + '_' + b.getVersion();
        logger.log(Level.INFO, "{0} removed", realID);

        // remove module properties
        synchronized (mapServiceProps) {
            mapServiceProps.remove(bid);
        }

        // cancel the scheduled removal
        TimerTask task = expiryMap.remove(b.getBundleId());
        if (task != null) {
            task.cancel();
        }

        // remove the module's packet filter, if there is one
        String filter = filterMap.remove(b.getBundleId());
        if (filter != null) {
            try {
                StringBuilder out = new StringBuilder();
                int res = Util.executeProcess(out,
                        "iptables -t mangle -D PREROUTING " + filter);
                if (res == 0) {
                    logger.log(Level.INFO, "{0}'s filter successfully removed: {1}",
                            new Object[]{realID, filter});
                } else {
                    logger.log(Level.INFO, "iptables failed: \n{0}", out);
                }
            } catch (IOException ex) {
                logger.info("iptables threw IOException");
            }
        }

        // notify all module listeners 
        String[] moduleAdded = new String[0];
        String[] moduleRemoved = new String[]{realID};
        String[] currentModules = getModulesForState(Bundle.ACTIVE).toArray(new String[0]);
        synchronized (serviceListeners) {
            for (ServiceListener l : serviceListeners) {
                l.serviceChanged(new ServiceEvent(
                        moduleAdded,
                        moduleRemoved,
                        currentModules));
            }
        }
    }

    /**
     * moduleID is name[_version], where it will match any version if
     * version is omitted.
     * 
     * returns: "bundleId state symbolicName version\n"
     * @param moduleID
     * @return 
     */
    synchronized StringBuilder getModuleStatus(String moduleID) {
        String name = null;
        String version = null;

        if (moduleID != null && moduleID.length() > 0) {
            String[] a = moduleID.split("_", 2);
            if (a.length > 0) {
                name = a[0];
                if (a.length > 1) {
                    version = a[1];
                }
            }
        }

        StringBuilder s = new StringBuilder();
        final char SPACE = ' ';
        BundleContext context = framework.getBundleContext();
        Bundle[] bundles = context.getBundles();
        for (Bundle b : bundles) {

            if (name != null) {
                if (!name.equals(b.getSymbolicName())) {
                    continue;
                }
                if (version != null) {
                    if (!version.equals(b.getVersion().toString())) {
                        continue;
                    }
                }
            }

            s.append(b.getBundleId()).append(SPACE);
            s.append(this.getStateName(b.getState())).append(SPACE);
            s.append(b.getSymbolicName()).append(SPACE);
            s.append(b.getVersion()).append("\n");
        }
        return s;
    }

    private String getStateName(int state) {
        switch (state) {
            case Bundle.UNINSTALLED:
                return "UNINSTALLED";
            case Bundle.INSTALLED:
                return "INSTALLED";
            case Bundle.RESOLVED:
                return "RESOLVED";
            case Bundle.STARTING:
                return "STARTING";
            case Bundle.STOPPING:
                return "STOPPING";
            case Bundle.ACTIVE:
                return "ACTIVE";
            default:
                return Integer.toHexString(state);
        }
    }

    /**
     * returns all the bundles which are in asked @state
     * @param state
     * @return 
     */
    synchronized ArrayList<String> getModulesForState(int state) {
        ArrayList<String> modules = new ArrayList<String>();
        BundleContext context = framework.getBundleContext();
        Bundle[] bundles = context.getBundles();
        for (Bundle b : bundles) {
            if (b.getState() == state) {
                modules.add(b.getSymbolicName() + "_" + b.getVersion());
            }
        }
        return modules;
    }
}
