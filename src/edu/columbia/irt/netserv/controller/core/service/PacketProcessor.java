package edu.columbia.irt.netserv.controller.core.service;

import java.nio.ByteBuffer;
import java.util.Comparator;

public interface PacketProcessor {

    public void processPacket(ByteBuffer buf);

    /**
     * Returns an integer that indicates the relative order of this
     * PacketProcessor compared to the others in the packet processing
     * chain.  If the order doesn't matter, return 0, which is the
     * neutral position.
     */
    public int relativeOrder();
    public static final Comparator<PacketProcessor> comparator =
            new Comparator<PacketProcessor>() {

                @Override
                public int compare(PacketProcessor p1, PacketProcessor p2) {
                    return p1.relativeOrder() - p2.relativeOrder();
                }
            };
}
