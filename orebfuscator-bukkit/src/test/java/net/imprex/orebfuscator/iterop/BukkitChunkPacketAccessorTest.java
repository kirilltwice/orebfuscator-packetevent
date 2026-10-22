package net.imprex.orebfuscator.iterop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.imprex.orebfuscator.obfuscation.ObfuscationResponse;
import dev.imprex.orebfuscator.util.BlockPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import net.imprex.orebfuscator.util.PacketBuffers;
import org.junit.jupiter.api.Test;

class BukkitChunkPacketAccessorTest {

  private static final int PACKET_ID = 0x27;
  private static final int CHUNK_X = -3;
  private static final int CHUNK_Z = 7;
  private static final byte[] LIGHT_DATA = {1, 2, 3, 4, 5, 6, 7, 8, 9};

  private static void writeHeightmaps(ByteBuf buffer) {
    PacketBuffers.writeVarInt(buffer, 2);
    for (int type = 1; type <= 2; type++) {
      PacketBuffers.writeVarInt(buffer, type);
      PacketBuffers.writeVarInt(buffer, 37);
      for (int i = 0; i < 37; i++) {
        buffer.writeLong(Long.MAX_VALUE - i * type);
      }
    }
  }

  private static void writeString(ByteBuf buffer, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    buffer.writeShort(bytes.length);
    buffer.writeBytes(bytes);
  }

  private static void writeBlockEntity(ByteBuf buffer, int x, int y, int z, boolean withTag) {
    buffer.writeByte((x & 15) << 4 | (z & 15));
    buffer.writeShort(y);
    PacketBuffers.writeVarInt(buffer, 300);

    if (!withTag) {
      buffer.writeByte(0);
      return;
    }

    buffer.writeByte(10);
    buffer.writeByte(8);
    writeString(buffer, "id");
    writeString(buffer, "minecraft:chest");
    buffer.writeByte(9);
    writeString(buffer, "Items");
    buffer.writeByte(10);
    buffer.writeInt(1);
    buffer.writeByte(1);
    writeString(buffer, "Slot");
    buffer.writeByte(3);
    buffer.writeByte(12);
    writeString(buffer, "longs");
    buffer.writeInt(2);
    buffer.writeLong(1L);
    buffer.writeLong(2L);
    buffer.writeByte(0);
    buffer.writeByte(9);
    writeString(buffer, "Ints");
    buffer.writeByte(3);
    buffer.writeInt(3);
    buffer.writeInt(1);
    buffer.writeInt(2);
    buffer.writeInt(3);
    buffer.writeByte(0);
  }

  private static ByteBuf createPacket(byte[] data, boolean[] blockEntitiesWithTag, int[][] positions) {
    ByteBuf buffer = Unpooled.buffer();
    PacketBuffers.writeVarInt(buffer, PACKET_ID);
    buffer.writeInt(CHUNK_X);
    buffer.writeInt(CHUNK_Z);
    writeHeightmaps(buffer);
    PacketBuffers.writeVarInt(buffer, data.length);
    buffer.writeBytes(data);
    PacketBuffers.writeVarInt(buffer, positions.length);
    for (int i = 0; i < positions.length; i++) {
      writeBlockEntity(buffer, positions[i][0], positions[i][1], positions[i][2], blockEntitiesWithTag[i]);
    }
    buffer.writeBytes(LIGHT_DATA);
    return buffer;
  }

  private static byte[] bytes(ByteBuf buffer) {
    byte[] bytes = new byte[buffer.readableBytes()];
    buffer.getBytes(buffer.readerIndex(), bytes);
    return bytes;
  }

  @Test
  void testParseAndRewrite() {
    byte[] data = new byte[300];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) i;
    }

    int[][] positions = {{1, -60, 2}, {15, 100, 0}, {4, 5, 6}};
    ByteBuf packet = createPacket(data, new boolean[]{true, false, true}, positions);

    var accessor = new BukkitChunkPacketAccessor(packet);
    assertEquals(CHUNK_X, accessor.chunkX());
    assertEquals(CHUNK_Z, accessor.chunkZ());
    assertArrayEquals(data, accessor.data());

    byte[] obfuscated = new byte[1000];
    obfuscated[999] = 42;
    var removed = new BlockPos((CHUNK_X << 4) + 15, 100, (CHUNK_Z << 4));
    var removedTagged = new BlockPos((CHUNK_X << 4) + 4, 5, (CHUNK_Z << 4) + 6);
    accessor.update(new ObfuscationResponse(obfuscated, Set.of(removed, removedTagged), List.of()));

    ByteBuf result = accessor.take();
    assertNotNull(result);
    assertNull(accessor.take());

    ByteBuf expected = createPacket(obfuscated, new boolean[]{true}, new int[][]{positions[0]});
    assertArrayEquals(bytes(expected), bytes(result));
    assertEquals(0, packet.refCnt());

    result.release();
  }

  @Test
  void testRewriteWithoutBlockEntityRemoval() {
    byte[] data = {9, 8, 7};
    int[][] positions = {{0, 0, 0}};
    ByteBuf packet = createPacket(data, new boolean[]{true}, positions);

    var accessor = new BukkitChunkPacketAccessor(packet);
    byte[] obfuscated = {1, 1, 1, 1};
    accessor.update(new ObfuscationResponse(obfuscated, Set.of(), List.of()));

    ByteBuf result = accessor.take();
    assertNotNull(result);
    assertArrayEquals(bytes(createPacket(obfuscated, new boolean[]{true}, positions)), bytes(result));
    result.release();
  }

  @Test
  void testReleaseBeforeUpdate() {
    ByteBuf packet = createPacket(new byte[]{1}, new boolean[0], new int[0][]);
    var accessor = new BukkitChunkPacketAccessor(packet);

    accessor.release();
    assertEquals(0, packet.refCnt());

    accessor.update(new ObfuscationResponse(new byte[]{2}, Set.of(), List.of()));
    assertNull(accessor.take());
  }

  @Test
  void testVarIntSize() {
    for (int value : new int[]{0, 1, 127, 128, 16383, 16384, 2097151, 2097152, Integer.MAX_VALUE, -1}) {
      ByteBuf buffer = Unpooled.buffer();
      PacketBuffers.writeVarInt(buffer, value);
      assertEquals(buffer.readableBytes(), PacketBuffers.varIntSize(value));
      assertEquals(value, PacketBuffers.getVarInt(buffer, 0));
      assertEquals(buffer.readableBytes(), PacketBuffers.skipVarInt(buffer, 0));
    }
  }
}
