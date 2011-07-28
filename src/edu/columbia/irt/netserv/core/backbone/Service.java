package edu.columbia.irt.netserv.core.backbone;

import java.io.OutputStream;
import org.osgi.framework.BundleActivator;

/**
 * All the new services have to implement this interface, so that they can register their
 * packet processing methods.
 * @author amanus
 */
public interface Service extends BundleActivator, PacketProcessor {

    public void execute(OutputStream out, Object param) throws ServiceException;
    /**
     * gives the instance of current class
     * @return 
     */
    public Object getInstance();
}
