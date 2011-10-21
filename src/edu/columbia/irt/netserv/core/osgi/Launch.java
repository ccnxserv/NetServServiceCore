package edu.columbia.irt.netserv.core.osgi;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.*;

import org.osgi.framework.*;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public class Launch {

    static final boolean DEBUG = false;
    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.core.osgi");

    public static void main(String[] args) throws Exception {
        launch("server");
    }

    public static Controller launch(String mode) throws Exception {
        Properties prop = new Properties();
        /*
        String fileName = "netserv.config";
        try {
        InputStream is = new FileInputStream(fileName);
        prop.load(is);
        } catch (IOException e) {
        e.toString();
        }
        String base = prop.getProperty("bundle-location");
        String[] bundles = prop.getProperty("initial-bundles").split(",");
         */

        String base = "/node-repo";
        String[] bundles = {"servlet-api-2.5.jar", "jetty-util-6.1.24.jar",
            "jetty-6.1.24.jar", "netserv-service-core-0.0.1.jar"};

        Framework framework = launchOSGIFramework(
                "org.eclipse.osgi.launch.EquinoxFactory", base, bundles);

        Controller osgi;
        if (mode.equalsIgnoreCase("server")) {
            String ctrlPortStr = System.getProperty("netserv.container.ctrlport");
            if (ctrlPortStr == null) {
                if (DEBUG) {
                    ctrlPortStr = "7001";
                } else {
                    String err = "System property netserv.container.ctrlport not found";
                    logger.severe(err);
                    throw new Exception(err);
                }
            }

            final int ctrlPort = Integer.parseInt(ctrlPortStr);
            osgi = new Controller(framework, ctrlPort);
            osgi.start();
        } else {
            osgi = new Controller(framework);
        }

        return osgi;

    }

    private static Framework launchOSGIFramework(String factoryName,
            String base, String[] bundlesToInstall) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, BundleException {

        Map<String, String> configuration = new HashMap<String, String>();
        configuration.put("org.osgi.framework.system.packages.extra",
                "edu.columbia.irt.netserv.core.backbone");

        FrameworkFactory factory =
                (FrameworkFactory) Class.forName(factoryName).newInstance();
        Framework framework = factory.newFramework(configuration);
        framework.init();

        BundleContext context = framework.getBundleContext();
        Bundle[] cache = context.getBundles();

        for (Bundle b : cache) {
            if (b.getBundleId() != framework.getBundleId()) {
                b.uninstall();
            }
        }
        for (String bundle : bundlesToInstall) {
            String[] bundleId = bundle.split("::");
            logger.log(Level.INFO, "Installing {0} bundle ..", bundle);
            Bundle b = context.installBundle("file:" + base + "/" + bundleId[0].trim());
            b.start();
            if (bundleId.length > 1) {
                Controller.serviceMap.put(bundleId[1], b);
            }
        }
        framework.start();
        return framework;
    }
}
