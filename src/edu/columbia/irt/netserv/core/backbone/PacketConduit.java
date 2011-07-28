package edu.columbia.irt.netserv.core.backbone;

import java.util.logging.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

public class PacketConduit implements PacketDispatcher {

    static final Logger logger = Logger.getLogger("edu.columbia.irt.netserv.controller.core.service");

    static {
        Util.setupConsoleLogging(logger, Level.INFO);
    }
    private final ArrayList<PacketProcessor> processors =
            new ArrayList<PacketProcessor>();
    private static PacketDispatcher dispatcher = null;

    public void setDispatch(PacketDispatcher d) {
        PacketConduit.dispatcher = d;
    }

    @Override
    public void addProcessor(PacketProcessor p) {
        synchronized (processors) {
            processors.add(p);
            Collections.sort(processors, PacketProcessor.comparator);
        }
        logProcessorList();
    }

    @Override
    public void removeProcessor(PacketProcessor p) {
        synchronized (processors) {
            processors.remove(p);
        }
        logProcessorList();
    }

    @Override
    public void dispatchPacket(ByteBuffer buf) {
        synchronized (processors) {
            for (PacketProcessor p : processors) {
                p.processPacket(buf);
            }
        }
    }

    public void logProcessorList() {
        StringBuilder s = new StringBuilder("PktProcessor list:");
        synchronized (processors) {
            for (PacketProcessor p : processors) {
                s.append("\n  ").append(p.relativeOrder()).append(": ").append(p);
            }
        }
        logger.info(s.toString());
    }
    
    /**
     * This method is called by native C++ code
     * @param buf 
     */
    private static void injectPacket(ByteBuffer buf) {
        dispatcher.dispatchPacket(buf);
    }
}