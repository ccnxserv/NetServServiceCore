package edu.columbia.irt.netserv.controller.core;

import edu.columbia.irt.netserv.controller.core.service.Util;
import edu.columbia.irt.netserv.controller.core.service.PacketConduit;
import java.util.Properties;
import java.util.logging.*;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller.core");

    static {
        Util.setupConsoleLogging(logger, Level.INFO);
    }

    @Override
    public void start(BundleContext context) {
        Properties props = new Properties();
        props.put("version", "1.0");
        props.put("protocol", "udp,tcp,icmp");

        logger.log(Level.INFO, "registering PacketConduit with {0}", props);
        PacketConduit pc = new PacketConduit();
        pc.setDispatch(pc);
        
        context.registerService(
                PacketConduit.class.getName(),
                pc, props);
    }

    @Override
    public void stop(BundleContext context) {
        // NOTE: The service is automatically unregistered.
    }
}
