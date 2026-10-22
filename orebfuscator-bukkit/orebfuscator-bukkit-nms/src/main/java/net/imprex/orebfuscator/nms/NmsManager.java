package net.imprex.orebfuscator.nms;

import dev.imprex.orebfuscator.cache.AbstractRegionFileCache;
import dev.imprex.orebfuscator.config.api.Config;
import dev.imprex.orebfuscator.interop.ChunkAccessor;
import dev.imprex.orebfuscator.interop.RegistryAccessor;
import dev.imprex.orebfuscator.util.BlockPos;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface NmsManager extends RegistryAccessor {

  AbstractRegionFileCache<?> createRegionFileCache(Config config);

  CompletableFuture<@Nullable ChunkAccessor> getChunkFuture(World world, int chunkX, int chunkZ);

  @Nullable ChunkAccessor getChunkNow(World world, int chunkX, int chunkZ);

  @Nullable ChunkAccessor getChunkIfLoaded(World world, int chunkX, int chunkZ);

  @Nullable Entity getEntity(World world, int entityId);

  void resendEntity(Player player, int entityId);

  void sendBlockUpdates(World world, Iterable<BlockPos> iterable);

  void sendBlockUpdates(Player player, Iterable<BlockPos> iterable);
}