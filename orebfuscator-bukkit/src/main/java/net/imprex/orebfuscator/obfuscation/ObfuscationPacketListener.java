package net.imprex.orebfuscator.obfuscation;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import dev.imprex.orebfuscator.PermissionRequirements;
import dev.imprex.orebfuscator.logging.LogLevel;
import dev.imprex.orebfuscator.logging.OfcLogger;
import dev.imprex.orebfuscator.obfuscation.ObfuscationPipeline;
import dev.imprex.orebfuscator.statistics.InjectorStatistics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.iterop.BukkitChunkPacketAccessor;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessor;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessorManager;
import net.imprex.orebfuscator.util.PacketBuffers;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ObfuscationPacketListener extends PacketListenerAbstract {

  private static final float DESIRED_CHUNKS_PER_TICK = 10f;

  private static final Set<PacketType.Play.Server> ORDERED_PACKETS = EnumSet.of(
      PacketType.Play.Server.CHUNK_DATA,
      PacketType.Play.Server.CHUNK_BATCH_BEGIN,
      PacketType.Play.Server.CHUNK_BATCH_END,
      PacketType.Play.Server.UNLOAD_CHUNK,
      PacketType.Play.Server.CHUNK_BIOMES,
      PacketType.Play.Server.UPDATE_LIGHT,
      PacketType.Play.Server.BLOCK_ENTITY_DATA,
      PacketType.Play.Server.BLOCK_CHANGE,
      PacketType.Play.Server.MULTI_BLOCK_CHANGE,
      PacketType.Play.Server.RESPAWN
  );

  private static final Set<PacketType.Play.Server> RESPAWN_PACKETS = EnumSet.of(
      PacketType.Play.Server.UPDATE_VIEW_DISTANCE,
      PacketType.Play.Server.UPDATE_VIEW_POSITION,
      PacketType.Play.Server.PLAYER_POSITION_AND_LOOK,
      PacketType.Play.Server.SPAWN_POSITION,
      PacketType.Play.Server.SERVER_DIFFICULTY,
      PacketType.Play.Server.SET_EXPERIENCE,
      PacketType.Play.Server.TIME_UPDATE,
      PacketType.Play.Server.CHANGE_GAME_STATE,
      PacketType.Play.Server.ENTITY_STATUS,
      PacketType.Play.Server.DECLARE_COMMANDS,
      PacketType.Play.Server.SOUND_EFFECT,
      PacketType.Play.Server.HELD_ITEM_CHANGE,
      PacketType.Play.Server.WINDOW_ITEMS,
      PacketType.Play.Server.WINDOW_PROPERTY,
      PacketType.Play.Server.SET_SLOT,
      PacketType.Play.Server.UPDATE_ATTRIBUTES,
      PacketType.Play.Server.UPDATE_HEALTH,
      PacketType.Play.Server.PLAYER_ABILITIES,
      PacketType.Play.Server.ENTITY_EFFECT,
      PacketType.Play.Server.UPDATE_SIMULATION_DISTANCE,
      PacketType.Play.Server.INITIALIZE_WORLD_BORDER
  );

  private final ObfuscationPipeline pipeline;
  private final InjectorStatistics statistics;
  private final BukkitPlayerAccessorManager playerManager;

  public ObfuscationPacketListener(Orebfuscator orebfuscator) {
    super(PacketListenerPriority.HIGHEST);

    this.pipeline = orebfuscator.obfuscationPipeline();
    this.statistics = orebfuscator.statistics().injector;
    this.playerManager = orebfuscator.playerManager();
  }

  @Override
  public void onPacketReceive(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CHUNK_BATCH_ACK || event.isCancelled()) {
      return;
    }

    ByteBuf buffer = PacketBuffers.buffer(event);
    buffer.setFloat(buffer.readerIndex(), DESIRED_CHUNKS_PER_TICK);
    this.statistics.injectorBatchSize.add(DESIRED_CHUNKS_PER_TICK);
  }

  @Override
  public void onPacketSend(PacketSendEvent event) {
    if (!(event.getPacketType() instanceof PacketType.Play.Server type) || event.isCancelled()) {
      return;
    }

    boolean respawnPacket = RESPAWN_PACKETS.contains(type);
    if (!respawnPacket && !ORDERED_PACKETS.contains(type)) {
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

    PacketQueue queue = player.packetQueue((Channel) event.getChannel());
    switch (type) {
      case CHUNK_DATA -> this.onChunk(event, player, queue);
      case CHUNK_BATCH_BEGIN -> this.onBatchStart(event, player, queue);
      case CHUNK_BATCH_END -> queue.finishBatch(event);
      case RESPAWN -> {
        player.startRespawn();
        queue.enqueueIfPending(event, true);
      }
      default -> {
        if (!respawnPacket || player.isRespawning() || queue.hasPendingRespawn()) {
          queue.enqueueIfPending(event, false);
        }
      }
    }
  }

  private boolean needsObfuscation(BukkitPlayerAccessor player) {
    return !player.hasPermission(PermissionRequirements.BYPASS) && player.world().config().needsObfuscation();
  }

  private void onBatchStart(PacketSendEvent event, BukkitPlayerAccessor player, PacketQueue queue) {
    if (this.needsObfuscation(player)) {
      queue.startBatch(event);
    } else {
      queue.enqueueIfPending(event, false);
    }
  }

  private void onChunk(PacketSendEvent event, BukkitPlayerAccessor player, PacketQueue queue) {
    if (!this.needsObfuscation(player)) {
      queue.enqueueIfPending(event, false);
      return;
    }

    var timer = this.statistics.injectorDelaySync.start();
    try {
      ByteBuf packet = PacketBuffers.slice(event);

      BukkitChunkPacketAccessor chunk;
      try {
        chunk = new BukkitChunkPacketAccessor(packet);
      } catch (RuntimeException e) {
        OfcLogger.throttle(LogLevel.WARN, "Unable to parse chunk packet, sending it without obfuscation");
        OfcLogger.debug(e.toString());
        queue.enqueueIfPending(event, false);
        return;
      }

      var world = player.world();

      CompletableFuture<Void> future;
      packet.retain();
      try {
        var neighbors = world.getLoadedNeighboringChunks(chunk.chunkX(), chunk.chunkZ());
        future = this.pipeline.request(world, player, chunk, neighbors).toCompletableFuture();
      } catch (RuntimeException e) {
        chunk.release();
        OfcLogger.error("Unable to request obfuscation for chunk", e);
        queue.enqueueIfPending(event, false);
        return;
      }

      event.setCancelled(true);
      queue.enqueueChunk(chunk, future);
    } finally {
      timer.stop();
    }
  }
}
