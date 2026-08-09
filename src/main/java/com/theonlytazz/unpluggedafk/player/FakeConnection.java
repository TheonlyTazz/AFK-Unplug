package com.theonlytazz.unpluggedafk.player;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

final class FakeConnection extends Connection {
    private static final SocketAddress LOOPBACK = new InetSocketAddress("127.0.0.1", 65535);

    FakeConnection() {
        super(PacketFlow.SERVERBOUND);
        this.channel = new EmbeddedChannel();
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        if (listener != null) channel.newSucceededFuture().addListener(listener);
    }

    @Override public void setReadOnly() {}
    @Override public void handleDisconnection() {}
    @Override public void tick() {}
    @Override public SocketAddress getRemoteAddress() { return LOOPBACK; }
}
