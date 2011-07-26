package edu.columbia.irt.netserv.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.*;

import org.osgi.framework.*;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public class NetServJavaController {

    static final boolean DEBUG = true;
    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller");

    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        String fileName = "netserv.config";
        try {
            InputStream is = new FileInputStream(fileName);
            prop.load(is);
        } catch (IOException e) {
            e.toString();
        }
        String base = prop.getProperty("bundle-location");
        String[] bundles = prop.getProperty("initial-bundles").split(",");
        System.out.println(prop.getProperty("bundle-location"));
        System.out.println(prop.getProperty("initial-bundles"));
        Framework framework = launchOSGIFramework(
                "org.eclipse.osgi.launch.EquinoxFactory", base, bundles);

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
        // initialize OSGi controller
        new OSGiController(framework, ctrlPort).start();
    }

    private static Framework launchOSGIFramework(String factoryName,
            String base, String[] bundlesToInstall) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, BundleException {

        Map<String, String> configuration = new HashMap<String, String>();
        configuration.put("org.osgi.framework.system.packages.extra",
                "edu.columbia.irt.netserv.controller.core.service");

        FrameworkFactory factory =
                (FrameworkFactory) Class.forName(factoryName).newInstance();
        Framework framework = factory.newFramework(configuration);
        framework.init();

        BundleContext context = framework.getBundleContext();
        for (String bundle : bundlesToInstall) {
            logger.log(Level.INFO, "Installing {0} bundle ..", bundle);
            Bundle b = context.installBundle("file:" + base + "/" + bundle.trim());
            b.start();
        }
        framework.start();
        return framework;
    }
}
