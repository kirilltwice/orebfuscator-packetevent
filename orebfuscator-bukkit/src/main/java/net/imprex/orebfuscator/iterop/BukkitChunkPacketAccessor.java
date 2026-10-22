package net.imprex.orebfuscator.iterop;

import dev.imprex.orebfuscator.interop.ChunkPacketAccessor;
import dev.imprex.orebfuscator.obfuscation.ObfuscationResponse;
import dev.imprex.orebfuscator.util.BlockPos;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import net.imprex.orebfuscator.util.PacketBuffers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BukkitChunkPacketAccessor implements ChunkPacketAccessor {

  private final int chunkX;
  private final int chunkZ;
  private final byte[] data;

  private final int dataLengthIndex;
  private final int blockEntitiesIndex;

  private @Nullable ByteBuf packet;

  public BukkitChunkPacketAccessor(ByteBuf packet) {
    int index = PacketBuffers.skipVarInt(packet, packet.readerIndex());

    this.chunkX = packet.getInt(index);
    this.chunkZ = packet.getInt(index + 4);
    index += 8;

    int heightmapCount = PacketBuffers.getVarInt(packet, index);
    index = PacketBuffers.skipVarInt(packet, index);
    for (int i = 0; i < heightmapCount; i++) {
      index = PacketBuffers.skipVarInt(packet, index);
      int longCount = PacketBuffers.getVarInt(packet, index);
      index = PacketBuffers.skipVarInt(packet, index) + longCount * Long.BYTES;
    }

    this.dataLengthIndex = index;
    int dataLength = PacketBuffers.getVarInt(packet, index);
    index = PacketBuffers.skipVarInt(packet, index);

    if (dataLength < 0 || index + dataLength > packet.writerIndex()) {
      throw new IllegalArgumentException("Invalid chunk data length: " + dataLength);
    }

    this.data = new byte[dataLength];
    packet.getBytes(index, this.data);
    this.blockEntitiesIndex = index + dataLength;

    this.packet = packet;
  }

  @Override
  public int chunkX() {
    return this.chunkX;
  }

  @Override
  public int chunkZ() {
    return this.chunkZ;
  }

  @Override
  public boolean isSectionPresent(int index) {
    return true;
  }

  @Override
  public byte[] data() {
    return this.data;
  }

  @Override
  public synchronized void update(ObfuscationResponse response) {
    ByteBuf source = this.packet;
    if (source == null) {
      return;
    }

    this.packet = rewrite(source, response.data(), response.blockEntities());
    source.release();
  }

  public synchronized @Nullable ByteBuf take() {
    ByteBuf buffer = this.packet;
    this.packet = null;
    return buffer;
  }

  public synchronized void release() {
    ByteBuf buffer = this.packet;
    this.packet = null;
    if (buffer != null) {
      buffer.release();
    }
  }

  private ByteBuf rewrite(ByteBuf source, byte[] data, Set<BlockPos> removedBlockEntities) {
    int start = source.readerIndex();
    int end = source.writerIndex();

    ByteBuf target = source.alloc().buffer(
        this.dataLengthIndex - start + 5 + data.length + end - this.blockEntitiesIndex);
    try {
      target.writeBytes(source, start, this.dataLengthIndex - start);
      PacketBuffers.writeVarInt(target, data.length);
      target.writeBytes(data);

      if (removedBlockEntities.isEmpty()) {
        target.writeBytes(source, this.blockEntitiesIndex, end - this.blockEntitiesIndex);
      } else {
        writeBlockEntities(source, target, removedBlockEntities);
      }
      return target;
    } catch (RuntimeException e) {
      target.release();
      throw e;
    }
  }

  private void writeBlockEntities(ByteBuf source, ByteBuf target, Set<BlockPos> removedBlockEntities) {
    int count = PacketBuffers.getVarInt(source, this.blockEntitiesIndex);
    int index = PacketBuffers.skipVarInt(source, this.blockEntitiesIndex);

    int[] keptRanges = new int[count * 2];
    int keptCount = 0;

    int baseX = this.chunkX << 4;
    int baseZ = this.chunkZ << 4;

    for (int i = 0; i < count; i++) {
      int entryStart = index;

      int packedXZ = source.getUnsignedByte(index);
      int y = source.getShort(index + 1);
      index = PacketBuffers.skipVarInt(source, index + 3);
      index = PacketBuffers.skipNbt(source, index);

      var position = new BlockPos(baseX + (packedXZ >> 4), y, baseZ + (packedXZ & 15));
      if (!removedBlockEntities.contains(position)) {
        keptRanges[keptCount * 2] = entryStart;
        keptRanges[keptCount * 2 + 1] = index;
        keptCount++;
      }
    }

    PacketBuffers.writeVarInt(target, keptCount);
    for (int i = 0; i < keptCount; i++) {
      int rangeStart = keptRanges[i * 2];
      target.writeBytes(source, rangeStart, keptRanges[i * 2 + 1] - rangeStart);
    }

    target.writeBytes(source, index, source.writerIndex() - index);
  }
}
