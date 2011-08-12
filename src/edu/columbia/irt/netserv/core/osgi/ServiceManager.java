package edu.columbia.irt.netserv.core.osgi;

import java.util.*;
import java.util.logging.*;

import org.osgi.framework.BundleContext;
import edu.columbia.irt.netserv.core.osgi.Controller;
import edu.columbia.irt.netserv.core.backbone.ServiceListener;
import edu.columbia.irt.netserv.core.backbone.Util;

public class ServiceManager {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller.core.service");

    static {
        Util.setupConsoleLogging(logger, Level.INFO);
    }

    public static Properties retrieveProperties(BundleContext context) {
        synchronized (Controller.mapServiceProps) {
            Properties props = Controller.mapServiceProps.get(context.getBundle().getBundleId());
            if (props != null) {
                return (Properties) props.clone();
            } else {
                return new Properties();
            }
        }
    }

    public static void addModuleListener(ServiceListener listener) {
        synchronized (Controller.serviceListeners) {
            Controller.serviceListeners.add(listener);
        }
        logListenerList();
    }

    public static void removeModuleListener(ServiceListener listener) {
        synchronized (Controller.serviceListeners) {
            Controller.serviceListeners.remove(listener);
        }
        logListenerList();
    }

    public static void logListenerList() {
        StringBuilder s = new StringBuilder("ModuleListener list:");
        synchronized (Controller.serviceListeners) {
            for (ServiceListener l : Controller.serviceListeners) {
                s.append("\n  ").append(l);
            }
        }
        logger.info(s.toString());
    }
}
