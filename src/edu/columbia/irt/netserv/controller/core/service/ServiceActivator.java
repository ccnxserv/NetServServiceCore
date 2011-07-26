package edu.columbia.irt.netserv.controller.core.service;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * All the new services have to extend this class, so that they can register their
 * packet processing methods.
 * @author amanus
 */
public class ServiceActivator implements BundleActivator, PacketProcessor {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller.core.service");

    static {
        Util.setupConsoleLogging(logger, Level.INFO);
    }
    PacketConduit svc = null;

    @Override
    public void start(BundleContext context) throws Exception {
        if (svc == null) {
            ServiceReference[] refs = context.getServiceReferences(
                    PacketConduit.class.getName(), "(version=1.0)");
            if (refs != null && refs.length > 0) {
                svc = (PacketConduit) context.getService(refs[0]);
            }
        }

        if (svc != null) {
            svc.addProcessor(this);
        } else {
            logger.warning("PacketProcessingService instance not found");
        }
    }

    @Override
    public void stop(BundleContext context) {
        // we assume PacketDispatchService never goes away.
        if (svc != null) {
            svc.removeProcessor(this);
        }
    }

    @Override
    public int relativeOrder() {
        return 0;
    }

    /**
     * Implements PacketProcessor.processPacket().
     * @param pkt the IP packet, including the IP header.
     */
    @Override
    public void processPacket(ByteBuffer pkt) {
    }
}
