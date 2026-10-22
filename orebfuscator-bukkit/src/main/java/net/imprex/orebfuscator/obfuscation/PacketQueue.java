package net.imprex.orebfuscator.obfuscation;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import dev.imprex.orebfuscator.logging.OfcLogger;
import dev.imprex.orebfuscator.statistics.InjectorStatistics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.imprex.orebfuscator.iterop.BukkitChunkPacketAccessor;
import net.imprex.orebfuscator.util.PacketBuffers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PacketQueue {

  private final Channel channel;
  private final InjectorStatistics statistics;

  private final ArrayDeque<PendingPacket> queue = new ArrayDeque<>();
  private final AtomicBoolean flushScheduled = new AtomicBoolean();

  private @Nullable PendingChunkBatch batch;
  private int pendingRespawns = 0;
  private boolean closed = false;

  public PacketQueue(Channel channel, InjectorStatistics statistics) {
    this.channel = channel;
    this.statistics = statistics;

    channel.closeFuture().addListener(future -> this.close());
  }

  public Channel channel() {
    return this.channel;
  }

  boolean isEmpty() {
    return this.queue.isEmpty();
  }

  boolean hasPendingRespawn() {
    return this.pendingRespawns > 0;
  }

  void enqueueIfPending(PacketSendEvent event, boolean respawn) {
    if (this.closed || this.queue.isEmpty()) {
      return;
    }

    this.queue.add(PendingPacket.raw(PacketBuffers.cancelAndRetain(event), respawn));
    if (respawn) {
      this.pendingRespawns++;
    }
  }

  void enqueueChunk(BukkitChunkPacketAccessor chunk, CompletableFuture<Void> future) {
    if (this.closed) {
      chunk.release();
      return;
    }

    var batch = this.batch;
    if (batch != null) {
      batch.addChunk(future);
    }

    this.queue.add(PendingPacket.chunk(chunk, future, batch != null));
    future.whenComplete((result, throwable) -> {
      if (throwable != null) {
        OfcLogger.error(throwable);
      }
      this.scheduleFlush();
    });
  }

  void startBatch(PacketSendEvent event) {
    if (this.closed) {
      return;
    }

    var previous = this.batch;
    if (previous != null) {
      previous.finish();
      OfcLogger.warn("Pending chunk batch discarded because a new batch was initiated.");
    }

    var batch = new PendingChunkBatch();
    this.batch = batch;
    this.queue.add(PendingPacket.batchStart(PacketBuffers.cancelAndRetain(event), batch));
    this.scheduleFlush();
  }

  void finishBatch(PacketSendEvent event) {
    var batch = this.batch;
    if (batch != null) {
      batch.finish();
      this.batch = null;
    }

    this.enqueueIfPending(event, false);
    this.scheduleFlush();
  }

  private void scheduleFlush() {
    if (this.flushScheduled.compareAndSet(false, true)) {
      this.channel.eventLoop().execute(() -> {
        this.flushScheduled.set(false);
        this.flush(false);
      });
    }
  }

  public void drain() {
    this.channel.eventLoop().execute(() -> {
      var batch = this.batch;
      if (batch != null) {
        batch.finish();
        this.batch = null;
      }
      this.flush(true);
    });
  }

  private void flush(boolean force) {
    if (this.closed || this.queue.isEmpty()) {
      return;
    }

    ChannelHandlerContext context = this.channel.pipeline().context(PacketEvents.ENCODER_NAME);
    if (context == null || !this.channel.isActive()) {
      this.close();
      return;
    }

    long now = System.nanoTime();
    boolean written = false;

    PendingPacket packet;
    while ((packet = this.queue.peek()) != null && (force || packet.isDone())) {
      this.queue.poll();

      if (packet.respawn) {
        this.pendingRespawns--;
      }

      long delay = now - packet.enqueuedAt;
      this.statistics.packetDelayAny.add(delay);
      if (packet.isDelayingChunks()) {
        this.statistics.packetDelayChunk.add(delay);
      }

      ByteBuf buffer = packet.take();
      if (buffer != null) {
        context.write(buffer, context.voidPromise());
        written = true;
      }
    }

    if (written) {
      context.flush();
    }
  }

  private void close() {
    this.closed = true;
    this.batch = null;
    this.pendingRespawns = 0;

    PendingPacket packet;
    while ((packet = this.queue.poll()) != null) {
      packet.release();
    }
  }
}
