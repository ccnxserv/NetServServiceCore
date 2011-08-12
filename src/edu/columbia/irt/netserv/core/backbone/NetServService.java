package edu.columbia.irt.netserv.core.backbone;

import java.io.OutputStream;

public interface NetServService {
    // used in server mode
    public void execute(OutputStream out, Object param) throws ServiceException;
    // returns the instance of current class
    public Object getInstance();
}
