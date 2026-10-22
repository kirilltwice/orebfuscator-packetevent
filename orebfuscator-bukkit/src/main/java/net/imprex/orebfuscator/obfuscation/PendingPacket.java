package net.imprex.orebfuscator.obfuscation;

import io.netty.buffer.ByteBuf;
import java.util.concurrent.CompletableFuture;
import net.imprex.orebfuscator.iterop.BukkitChunkPacketAccessor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class PendingPacket {

  static PendingPacket raw(ByteBuf buffer, boolean respawn) {
    return new PendingPacket(buffer, null, null, null, respawn, false);
  }

  static PendingPacket chunk(BukkitChunkPacketAccessor chunk, CompletableFuture<Void> future, boolean batched) {
    return new PendingPacket(null, chunk, future, null, false, batched);
  }

  static PendingPacket batchStart(ByteBuf buffer, PendingChunkBatch batch) {
    return new PendingPacket(buffer, null, null, batch, false, false);
  }

  final long enqueuedAt = System.nanoTime();
  final boolean respawn;
  private final boolean batched;

  private @Nullable ByteBuf buffer;
  private final @Nullable BukkitChunkPacketAccessor chunk;
  private final @Nullable CompletableFuture<Void> future;
  private final @Nullable PendingChunkBatch batch;

  private PendingPacket(@Nullable ByteBuf buffer, @Nullable BukkitChunkPacketAccessor chunk,
      @Nullable CompletableFuture<Void> future, @Nullable PendingChunkBatch batch, boolean respawn,
      boolean batched) {
    this.buffer = buffer;
    this.chunk = chunk;
    this.future = future;
    this.batch = batch;
    this.respawn = respawn;
    this.batched = batched;
  }

  boolean isDone() {
    if (this.batch != null) {
      return this.batch.isDone();
    }
    return this.future == null || this.future.isDone();
  }

  boolean isDelayingChunks() {
    return this.batch != null || (this.chunk != null && !this.batched);
  }

  @Nullable ByteBuf take() {
    if (this.chunk != null) {
      return this.chunk.take();
    }

    ByteBuf buffer = this.buffer;
    this.buffer = null;
    return buffer;
  }

  void release() {
    if (this.chunk != null) {
      this.chunk.release();
    } else if (this.buffer != null) {
      this.buffer.release();
      this.buffer = null;
    }
  }
}
