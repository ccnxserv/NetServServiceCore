package edu.columbia.irt.netserv.core.osgi;

import java.io.*;
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

public class Controller implements Runnable {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller");
    static final String NETSERV_SERVICE = "Netserv-Service";
    static final String CCN_SERVICE = "CCN-Service";

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
    // running mode 0 - server : 1 - embedded
    private int mode;
    private HashMap<Long, String> filterMap = new HashMap<Long, String>();
    private HashMap<Long, TimerTask> expiryMap = new HashMap<Long, TimerTask>();
    private Timer timer = new Timer("expiry timer", true);

    public Controller(Framework framework, int ctrlPort) {
        this.framework = framework;
        this.ctrlPort = ctrlPort;
        this.mode = 0;
    }

    public Controller(Framework framework) {
        this.framework = framework;
        this.mode = 1;
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
                    executeModule(arg, m, sock.getOutputStream());
                } else if (command.equals("SETUP")) {
                    setupModule(arg, m.get("url"), m);
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
            } catch (Exception ex) {
                out.printf("%d\n", -1); // failure
                logger.log(Level.SEVERE, "Execution exception:", ex);
                out.printf("Execution Exception\n\n");
            }
        }
    }

    /**
     * executeModule in embedded mode without outputStream instance
     * @param moduleID
     * @param headers 
     */
    public Object executeModule(String moduleID, HashMap<String, String> headers)
            throws Exception {

        logger.log(Level.INFO, "executeModule called {0} {1}",
                new Object[]{moduleID, headers});
        Bundle bundle = serviceMap.get(moduleID);
        if (bundle != null) {
            if (bundle.getState() != Bundle.ACTIVE) {
                bundle.start();
            }
            logger.log(Level.INFO, "Bundle {0} found in ACTIVE state", moduleID);
            String service = (String) bundle.getHeaders().get(Controller.CCN_SERVICE);

            Class serviceClass = bundle.loadClass(service);
            Object serviceObject = serviceClass.newInstance();
            Object param = null;
            if (headers.get("args") != null) {
                param = headers.get("args");
            }
            Method method = serviceClass.getMethod("execute", new Class[]{Object.class});
            Object ret = method.invoke(serviceObject, new Object[]{param});
            return ret;

        } else {
            return null;
        }
    }

    public void executeModule(String moduleID, HashMap<String, String> headers,
            OutputStream out) throws Exception {

        logger.log(Level.INFO, "executeModule called {0} {1}",
                new Object[]{moduleID, headers});
        Bundle bundle;
        synchronized (serviceMap) {
            bundle = serviceMap.get(moduleID);
        }
        if (bundle.getState() == Bundle.ACTIVE) {
            logger.log(Level.INFO, "Bundle {0} found in ACTIVE state", moduleID);
            String service = (String) bundle.getHeaders().get(Controller.NETSERV_SERVICE);
            logger.log(Level.INFO, "Netser-Service class = ", service);

            Class serviceClass = bundle.loadClass(service);
            Object serviceObject = serviceClass.newInstance();
            Object param = null;
            if (headers.get("args") != null) {
                param = headers.get("args");
            }
            Method method = serviceClass.getMethod("execute", new Class[]{OutputStream.class, Object.class});
            method.invoke(serviceObject, new Object[]{out, param});
        }
    }

    /**
     * Fetch a bundle from URL, install and start it, and schedule a
     * removal in TTL seconds.  If the bundle is already there, simply
     * refresh the TTL.
     * 
     * @param moduleID
     * @param url
     * @param headers
     * @throws BundleException 
     */
    public void setupModule(String moduleID, String url,
            HashMap<String, String> headers) throws BundleException {

        logger.log(Level.INFO, "setupModule called moduleID = {0}, url = {1}",
                new Object[]{moduleID, url});

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
        final long bid = bundle.getBundleId();
        
        if (this.mode == 0) {
            // controller is running in server mode
            TimerTask task = expiryMap.get(bid);
            if (task != null) {
                task.cancel();
            }
            // New TimerTask
            int ttl = 5 * 60;
            String ttlStr = headers.get("ttl");
            if (ttlStr != null) {
                ttl = Integer.parseInt(ttlStr);
            }
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
        }

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
    public void removeModule(String moduleID) throws BundleException {
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

    public String getModuleState(String moduleID) {
        Bundle bundle = serviceMap.get(moduleID);
        if (bundle != null) {
            return getStateName(bundle.getState());
        } else {
            return null;
        }
    }

    public void setModuleState(String moduleID, String state) {
        if (state != null & moduleID != null) {
            if (getStateName(Bundle.ACTIVE).equals(state)) {
                Bundle bundle = serviceMap.get(moduleID);
                try {
                    bundle.start();
                } catch (BundleException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
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
