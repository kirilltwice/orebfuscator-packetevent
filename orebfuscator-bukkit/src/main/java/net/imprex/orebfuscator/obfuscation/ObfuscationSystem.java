package net.imprex.orebfuscator.obfuscation;

import com.github.retrooper.packetevents.PacketEvents;
import dev.imprex.orebfuscator.obfuscation.DeobfuscationWorker;
import dev.imprex.orebfuscator.util.BlockPos;
import java.util.Collection;
import java.util.List;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessor;
import net.imprex.orebfuscator.iterop.BukkitWorldAccessorManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ObfuscationSystem {

  private final Orebfuscator orebfuscator;
  private final BukkitWorldAccessorManager worldManager;
  private final DeobfuscationWorker deobfuscationWorker;

  private @Nullable ObfuscationPacketListener packetListener;

  public ObfuscationSystem(Orebfuscator orebfuscator) {
    this.orebfuscator = orebfuscator;
    this.worldManager = orebfuscator.worldManager();

    this.deobfuscationWorker = new DeobfuscationWorker(orebfuscator);
    DeobfuscationListener.createAndRegister(orebfuscator, this);
  }

  public void registerChunkListener() {
    this.packetListener = new ObfuscationPacketListener(orebfuscator);
    PacketEvents.getAPI().getEventManager().registerListener(this.packetListener);
  }

  public void deobfuscate(Block block) {
    var world = this.worldManager.get(block.getWorld());
    var blockPos = new BlockPos(block.getX(), block.getY(), block.getZ());
    this.deobfuscationWorker.deobfuscate(world, blockPos);
  }

  public void deobfuscate(World world, List<BlockPos> blocks) {
    this.deobfuscationWorker.deobfuscate(this.worldManager.get(world), blocks);
  }

  public void deobfuscate(Collection<? extends Block> blocks) {
    if (blocks.isEmpty()) {
      return;
    }

    var world = this.worldManager.get(blocks.stream().findFirst().get().getWorld());
    var blockPos = blocks.stream()
        .map(block -> new BlockPos(block.getX(), block.getY(), block.getZ()))
        .toList();

    this.deobfuscationWorker.deobfuscate(world, blockPos);
  }

  public void shutdown() {
    if (this.packetListener == null) {
      return;
    }

    PacketEvents.getAPI().getEventManager().unregisterListener(this.packetListener);
    this.packetListener = null;

    for (var player : Bukkit.getOnlinePlayers()) {
      BukkitPlayerAccessor accessor = this.orebfuscator.playerManager().tryGet(player);
      var queue = accessor != null ? accessor.packetQueue() : null;
      if (queue != null) {
        queue.drain();
      }
    }
  }
}
