package edu.columbia.irt.netserv.controller.core.service;

import java.util.*;
import java.util.logging.*;

import org.osgi.framework.BundleContext;
import edu.columbia.irt.netserv.controller.OSGiController;

public class ServiceManager {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller.core.service");

    static {
        Util.setupConsoleLogging(logger, Level.INFO);
    }

    public static Properties retrieveProperties(BundleContext context) {
        synchronized (OSGiController.mapServiceProps) {
            Properties props = OSGiController.mapServiceProps.get(context.getBundle().getBundleId());
            if (props != null) {
                return (Properties) props.clone();
            } else {
                return new Properties();
            }
        }
    }

    public static void addModuleListener(ServiceListener listener) {
        synchronized (OSGiController.serviceListeners) {
            OSGiController.serviceListeners.add(listener);
        }
        logListenerList();
    }

    public static void removeModuleListener(ServiceListener listener) {
        synchronized (OSGiController.serviceListeners) {
            OSGiController.serviceListeners.remove(listener);
        }
        logListenerList();
    }

    public static void logListenerList() {
        StringBuilder s = new StringBuilder("ModuleListener list:");
        synchronized (OSGiController.serviceListeners) {
            for (ServiceListener l : OSGiController.serviceListeners) {
                s.append("\n  ").append(l);
            }
        }
        logger.info(s.toString());
    }
}
