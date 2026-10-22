package net.imprex.orebfuscator.entity;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.Set;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessor;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessorManager;
import net.imprex.orebfuscator.util.PacketBuffers;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EntityPacketListener extends PacketListenerAbstract {

  private static final Set<PacketType.Play.Server> ENTITY_PACKETS = EnumSet.of(
      PacketType.Play.Server.SPAWN_ENTITY,
      PacketType.Play.Server.DESTROY_ENTITIES,
      PacketType.Play.Server.ENTITY_STATUS,
      PacketType.Play.Server.ATTACH_ENTITY,
      PacketType.Play.Server.ENTITY_SOUND_EFFECT,
      PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
      PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION,
      PacketType.Play.Server.ENTITY_ROTATION,
      PacketType.Play.Server.ENTITY_HEAD_LOOK,
      PacketType.Play.Server.ENTITY_TELEPORT,
      PacketType.Play.Server.ENTITY_POSITION_SYNC,
      PacketType.Play.Server.ENTITY_VELOCITY,
      PacketType.Play.Server.ENTITY_METADATA,
      PacketType.Play.Server.ENTITY_EQUIPMENT,
      PacketType.Play.Server.ENTITY_ANIMATION,
      PacketType.Play.Server.ENTITY_EFFECT,
      PacketType.Play.Server.REMOVE_ENTITY_EFFECT,
      PacketType.Play.Server.UPDATE_ATTRIBUTES,
      PacketType.Play.Server.SET_PASSENGERS,
      PacketType.Play.Server.HURT_ANIMATION,
      PacketType.Play.Server.DAMAGE_EVENT,
      PacketType.Play.Server.COLLECT_ITEM,
      PacketType.Play.Server.MOVE_MINECART,
      PacketType.Play.Server.PROJECTILE_POWER,
      PacketType.Play.Server.SWING_ANIMATION
  );

  private final BukkitPlayerAccessorManager playerManager;

  public EntityPacketListener(BukkitPlayerAccessorManager playerManager) {
    super(PacketListenerPriority.NORMAL);
    this.playerManager = playerManager;
  }

  @Override
  public void onPacketSend(PacketSendEvent event) {
    if (!(event.getPacketType() instanceof PacketType.Play.Server type) || !ENTITY_PACKETS.contains(type)) {
      return;
    }

    Player bukkitPlayer = event.getPlayer();
    if (bukkitPlayer == null) {
      return;
    }

    BukkitPlayerAccessor player = this.playerManager.tryGet(bukkitPlayer);
    if (player == null) {
      return;
    }

    EntityHider hider = player.entityHider();
    ByteBuf buffer = PacketBuffers.buffer(event);
    int index = buffer.readerIndex();

    switch (type) {
      case SPAWN_ENTITY -> {
        int entityId = PacketBuffers.getVarInt(buffer, index);
        hider.onSpawn(entityId);
        if (hider.isHidden(entityId)) {
          event.setCancelled(true);
        }
      }
      case DESTROY_ENTITIES -> {
        int count = PacketBuffers.getVarInt(buffer, index);
        index = PacketBuffers.skipVarInt(buffer, index);
        for (int i = 0; i < count; i++) {
          hider.onDestroy(PacketBuffers.getVarInt(buffer, index));
          index = PacketBuffers.skipVarInt(buffer, index);
        }
      }
      default -> {
        if (hider.hasHidden() && hider.isHidden(readEntityId(type, buffer, index))) {
          event.setCancelled(true);
        }
      }
    }
  }

  private static int readEntityId(PacketType.Play.Server type, ByteBuf buffer, int index) {
    return switch (type) {
      case ENTITY_STATUS, ATTACH_ENTITY -> buffer.getInt(index);
      case ENTITY_SOUND_EFFECT -> {
        int soundId = PacketBuffers.getVarInt(buffer, index);
        index = PacketBuffers.skipVarInt(buffer, index);
        if (soundId == 0) {
          index = PacketBuffers.skipIdentifier(buffer, index);
          index = buffer.getBoolean(index) ? index + 1 + Float.BYTES : index + 1;
        }
        index = PacketBuffers.skipVarInt(buffer, index);
        yield PacketBuffers.getVarInt(buffer, index);
      }
      default -> PacketBuffers.getVarInt(buffer, index);
    };
  }
}
