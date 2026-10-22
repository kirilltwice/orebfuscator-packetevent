package net.imprex.orebfuscator.util;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PacketBuffers {

  private PacketBuffers() {
  }

  public static ByteBuf buffer(ProtocolPacketEvent event) {
    return (ByteBuf) event.getByteBuf();
  }

  public static ByteBuf slice(ProtocolPacketEvent event) {
    ByteBuf buffer = buffer(event);
    int start = packetStart(event);
    return buffer.slice(start, buffer.writerIndex() - start);
  }

  public static ByteBuf cancelAndRetain(ProtocolPacketEvent event) {
    ByteBuf packet = slice(event).retain();
    event.setCancelled(true);
    return packet;
  }

  private static int packetStart(ProtocolPacketEvent event) {
    ByteBuf buffer = buffer(event);
    PacketWrapper<?> wrapper = event.getLastUsedWrapper();
    if (wrapper != null && event.needsReEncode()) {
      buffer.clear();
      wrapper.writeVarInt(event.getPacketId());
      wrapper.write();
      event.setLastUsedWrapper(null);
      buffer.readerIndex(varIntSize(event.getPacketId()));
      return 0;
    }
    return buffer.readerIndex() - varIntSize(event.getPacketId());
  }

  public static int varIntSize(int value) {
    return (31 - Integer.numberOfLeadingZeros(value | 1)) / 7 + 1;
  }

  public static int getVarInt(ByteBuf buffer, int index) {
    int value = 0;
    for (int shift = 0; shift < 35; shift += 7) {
      byte b = buffer.getByte(index++);
      value |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return value;
      }
    }
    throw new IllegalArgumentException("VarInt too big");
  }

  public static int skipVarInt(ByteBuf buffer, int index) {
    for (int i = 0; i < 5; i++) {
      if ((buffer.getByte(index++) & 0x80) == 0) {
        return index;
      }
    }
    throw new IllegalArgumentException("VarInt too big");
  }

  public static void writeVarInt(ByteBuf buffer, int value) {
    while ((value & ~0x7F) != 0) {
      buffer.writeByte((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    buffer.writeByte(value);
  }

  public static int skipIdentifier(ByteBuf buffer, int index) {
    int length = getVarInt(buffer, index);
    return skipVarInt(buffer, index) + length;
  }

  public static int skipNbt(ByteBuf buffer, int index) {
    byte type = buffer.getByte(index++);
    return type == 0 ? index : skipNbtPayload(buffer, index, type);
  }

  private static int skipNbtPayload(ByteBuf buffer, int index, byte type) {
    return switch (type) {
      case 1 -> index + 1;
      case 2 -> index + 2;
      case 3, 5 -> index + 4;
      case 4, 6 -> index + 8;
      case 7 -> index + 4 + buffer.getInt(index);
      case 8 -> index + 2 + buffer.getUnsignedShort(index);
      case 9 -> skipNbtList(buffer, index);
      case 10 -> skipNbtCompound(buffer, index);
      case 11 -> index + 4 + 4 * buffer.getInt(index);
      case 12 -> index + 4 + 8 * buffer.getInt(index);
      default -> throw new IllegalArgumentException("Invalid NBT tag type: " + type);
    };
  }

  private static int skipNbtList(ByteBuf buffer, int index) {
    byte elementType = buffer.getByte(index);
    int length = Math.max(buffer.getInt(index + 1), 0);
    index += 5;

    int fixedSize = switch (elementType) {
      case 0 -> 0;
      case 1 -> 1;
      case 2 -> 2;
      case 3, 5 -> 4;
      case 4, 6 -> 8;
      default -> -1;
    };

    if (fixedSize >= 0) {
      return index + fixedSize * length;
    }

    for (int i = 0; i < length; i++) {
      index = skipNbtPayload(buffer, index, elementType);
    }
    return index;
  }

  private static int skipNbtCompound(ByteBuf buffer, int index) {
    while (true) {
      byte type = buffer.getByte(index++);
      if (type == 0) {
        return index;
      }
      index += 2 + buffer.getUnsignedShort(index);
      index = skipNbtPayload(buffer, index, type);
    }
  }
}
