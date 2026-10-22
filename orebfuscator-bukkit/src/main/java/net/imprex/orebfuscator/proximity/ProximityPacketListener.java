package net.imprex.orebfuscator.proximity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import dev.imprex.orebfuscator.PermissionRequirements;
import dev.imprex.orebfuscator.config.api.ProximityConfig;
import io.netty.buffer.ByteBuf;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessor;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessorManager;
import net.imprex.orebfuscator.iterop.BukkitWorldAccessor;
import net.imprex.orebfuscator.util.PacketBuffers;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ProximityPacketListener extends PacketListenerAbstract {

  private final BukkitPlayerAccessorManager playerManager;

  public ProximityPacketListener(Orebfuscator orebfuscator) {
    super(PacketListenerPriority.NORMAL);

    this.playerManager = orebfuscator.playerManager();

    PacketEvents.getAPI().getEventManager().registerListener(this);
  }

  public void unregister() {
    PacketEvents.getAPI().getEventManager().unregisterListener(this);
  }

  @Override
  public void onPacketSend(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.UNLOAD_CHUNK) {
      return;
    }

    Player bukkitPlayer = event.getPlayer();
    if (bukkitPlayer == null) {
      return;
    }

    BukkitPlayerAccessor player = this.playerManager.tryGet(bukkitPlayer);
    if (player == null || player.hasPermission(PermissionRequirements.BYPASS)) {
      return;
    }

    BukkitWorldAccessor world = player.world();
    ProximityConfig proximityConfig = world.config().proximity();
    if (proximityConfig == null || !proximityConfig.isEnabled()) {
      return;
    }

    ByteBuf buffer = PacketBuffers.buffer(event);
    long chunkPos = buffer.getLong(buffer.readerIndex());
    player.orebfuscatorPlayer().removeChunk(world, (int) chunkPos, (int) (chunkPos >>> 32));
  }
}
