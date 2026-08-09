package com.theonlytazz.unpluggedafk.player;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

final class FakeConnection extends Connection {
    private static final SocketAddress LOOPBACK = new InetSocketAddress("127.0.0.1", 65535);

    FakeConnection() {
        super(PacketFlow.SERVERBOUND);
        this.channel = new EmbeddedChannel();
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        if (listener != null) listener.onSuccess();
    }

    @Override public void setReadOnly() {}
    @Override public void handleDisconnection() {}
    @Override public void tick() {}
    @Override public SocketAddress getRemoteAddress() { return LOOPBACK; }
}
