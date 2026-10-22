package net.imprex.orebfuscator.iterop;

import dev.imprex.orebfuscator.PermissionRequirements;
import dev.imprex.orebfuscator.interop.PlayerAccessor;
import dev.imprex.orebfuscator.player.OrebfuscatorPlayer;
import dev.imprex.orebfuscator.util.BlockPos;
import dev.imprex.orebfuscator.util.EntityPose;
import io.netty.channel.Channel;
import java.util.Objects;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.OrebfuscatorCompatibility;
import net.imprex.orebfuscator.OrebfuscatorNms;
import net.imprex.orebfuscator.entity.EntityHider;
import net.imprex.orebfuscator.obfuscation.PacketQueue;
import net.imprex.orebfuscator.util.PermissionUtil;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class BukkitPlayerAccessor implements PlayerAccessor {

  private final Orebfuscator orebfuscator;
  private final Player player;
  private BukkitWorldAccessor world;

  private final BukkitWorldAccessorManager worldManager;
  private final OrebfuscatorPlayer orebfuscatorPlayer;

  private final EntityHider entityHider;
  private volatile @Nullable PacketQueue packetQueue;
  private volatile boolean respawning = false;

  public BukkitPlayerAccessor(Orebfuscator orebfuscator, Player player) {
    this.orebfuscator = orebfuscator;
    this.player = player;
    this.worldManager = orebfuscator.worldManager();
    this.world = this.worldManager.get(player.getWorld());
    this.orebfuscatorPlayer = new OrebfuscatorPlayer(orebfuscator, this);
    this.entityHider = new EntityHider(orebfuscator, this);
  }

  public Player player() {
    return this.player;
  }

  public EntityHider entityHider() {
    return this.entityHider;
  }

  public PacketQueue packetQueue(Channel channel) {
    PacketQueue queue = this.packetQueue;
    if (queue == null || queue.channel() != channel) {
      queue = new PacketQueue(channel, this.orebfuscator.statistics().injector);
      this.packetQueue = queue;
    }
    return queue;
  }

  public @Nullable PacketQueue packetQueue() {
    return this.packetQueue;
  }

  public void startRespawn() {
    this.respawning = true;

    this.runForPlayer(() -> {
      this.respawning = false;
    });
  }

  public boolean isRespawning() {
    return this.respawning;
  }

  public void changeWorld(BukkitWorldAccessor world) {
    this.world = world;
    this.orebfuscatorPlayer.clearChunks();
  }

  @Override
  public OrebfuscatorPlayer orebfuscatorPlayer() {
    return this.orebfuscatorPlayer;
  }

  @Override
  public EntityPose pose() {
    var location = player.getLocation();
    return new EntityPose(world, location.getX(), location.getY(), location.getZ(), location.getPitch(),
        location.getYaw());
  }

  @Override
  public EntityPose eyePose() {
    var location = player.getEyeLocation();
    return new EntityPose(world, location.getX(), location.getY(), location.getZ(), location.getPitch(),
        location.getYaw());
  }

  @Override
  public BukkitWorldAccessor world() {
    World bukkitWorld = player.getWorld();

    if (!Objects.equals(this.world.world, bukkitWorld)) {
      this.changeWorld(this.worldManager.get(bukkitWorld));
    }

    return world;
  }

  @Override
  public boolean isAlive() {
    return !player.isDead();
  }

  @Override
  public boolean isSpectator() {
    return player.getGameMode() == GameMode.SPECTATOR;
  }

  @Override
  public double lavaFogDistance() {
    return player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE) ? 7 : 2;
  }

  @Override
  public boolean hasPermission(PermissionRequirements permission) {
    return PermissionUtil.hasPermission(player, permission);
  }

  @Override
  public void runForPlayer(Runnable runnable) {
    OrebfuscatorCompatibility.runForPlayer(player, runnable);
  }

  @Override
  public void sendBlockUpdates(Iterable<BlockPos> iterable) {
    OrebfuscatorNms.sendBlockUpdates(player, iterable);
  }

  @Override
  public String toString() {
    return this.player.toString();
  }
}
