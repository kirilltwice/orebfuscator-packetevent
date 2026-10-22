package net.imprex.orebfuscator.entity;

import com.github.retrooper.packetevents.PacketEvents;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.OrebfuscatorCompatibility;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessor;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessorManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EntityHiderSystem implements Listener {

  private static final long TICK_PERIOD = 2L;

  private final BukkitPlayerAccessorManager playerManager;
  private final EntityPacketListener packetListener;

  private volatile boolean running = true;

  public EntityHiderSystem(Orebfuscator orebfuscator) {
    this.playerManager = orebfuscator.playerManager();
    this.packetListener = new EntityPacketListener(this.playerManager);

    PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
    Bukkit.getPluginManager().registerEvents(this, orebfuscator);

    for (Player player : Bukkit.getOnlinePlayers()) {
      this.start(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    this.start(event.getPlayer());
  }

  private void start(Player player) {
    OrebfuscatorCompatibility.runForPlayerTimer(player, TICK_PERIOD, () -> {
      if (!this.running) {
        return false;
      }

      BukkitPlayerAccessor accessor = this.playerManager.tryGet(player);
      if (accessor != null) {
        accessor.entityHider().tick();
      }
      return true;
    });
  }

  public void shutdown() {
    this.running = false;
    HandlerList.unregisterAll(this);

    if (OrebfuscatorCompatibility.isGameThread()) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        BukkitPlayerAccessor accessor = this.playerManager.tryGet(player);
        if (accessor != null) {
          accessor.entityHider().showAll();
        }
      }
    }

    PacketEvents.getAPI().getEventManager().unregisterListener(this.packetListener);
  }
}
