package edu.columbia.irt.netserv.core.backbone;

import java.nio.ByteBuffer;

public interface PacketDispatcher {

    public void dispatchPacket(ByteBuffer buf);

    public void addProcessor(PacketProcessor processor);

    public void removeProcessor(PacketProcessor processor);
}
