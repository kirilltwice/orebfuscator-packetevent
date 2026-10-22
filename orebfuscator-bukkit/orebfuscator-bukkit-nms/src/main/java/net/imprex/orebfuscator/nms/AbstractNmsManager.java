package net.imprex.orebfuscator.nms;

import dev.imprex.orebfuscator.reflect.Reflector;
import dev.imprex.orebfuscator.reflect.accessor.MethodAccessor;
import dev.imprex.orebfuscator.util.BlockProperties;
import dev.imprex.orebfuscator.util.BlockStateProperties;
import dev.imprex.orebfuscator.util.BlockTag;
import dev.imprex.orebfuscator.util.NamespacedKey;
import dev.imprex.orebfuscator.util.QuickMaths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public abstract class AbstractNmsManager implements NmsManager {

  private static final NamespacedKey BLOCK_ENTITIES_TAG = NamespacedKey.parse("orebfuscator:block_entities");
  private static final NamespacedKey MOVING_PISTON = NamespacedKey.parse("minecraft:moving_piston");

  private static @Nullable MethodAccessor worldGetHandle;
  private static @Nullable MethodAccessor playerGetHandle;

  protected static <T> T worldHandle(World world, Class<T> targetClass) {
    if (worldGetHandle == null) {
      worldGetHandle = Reflector.of(world.getClass()).method()
          .banStatic()
          .nameIs("getHandle")
          .returnType().is(targetClass)
          .parameterCount(0)
          .firstOrThrow();
    }
    return targetClass.cast(worldGetHandle.invoke(world));
  }

  protected static <T> T playerHandle(Player player, Class<T> targetClass) {
    if (playerGetHandle == null) {
      playerGetHandle = Reflector.of(player.getClass()).method()
          .banStatic()
          .nameIs("getHandle")
          .returnType().is(targetClass)
          .parameterCount(0)
          .firstOrThrow();
    }
    return targetClass.cast(playerGetHandle.invoke(player));
  }

  private final int uniqueBlockStateCount;
  private final int maxBitsPerBlockState;

  private final BlockStateProperties[] blockStates;
  private final Map<NamespacedKey, BlockProperties> blocks = new HashMap<>();
  protected final Map<NamespacedKey, BlockTag> tags = new HashMap<>();

  public AbstractNmsManager(int uniqueBlockStateCount) {
    this.uniqueBlockStateCount = uniqueBlockStateCount;
    this.maxBitsPerBlockState = QuickMaths.ceilLog2(uniqueBlockStateCount);

    this.blockStates = new BlockStateProperties[uniqueBlockStateCount];
  }

  protected final void registerBlockProperties(BlockProperties block) {
    this.blocks.put(block.getKey(), block);

    // TODO: add BitSet for each flag for (faster) cache friendly lookup
    for (BlockStateProperties blockState : block.getBlockStates()) {
      this.blockStates[blockState.getId()] = blockState;
    }
  }

  protected final void registerBlockTag(BlockTag tag) {
    this.tags.put(tag.key(), tag);
  }

  @Override
  public final int getUniqueBlockStateCount() {
    return this.uniqueBlockStateCount;
  }

  @Override
  public final int getMaxBitsPerBlockState() {
    return this.maxBitsPerBlockState;
  }

  @Override
  public final @Nullable BlockProperties getBlockByName(String name) {
    return NamespacedKey.tryParse(name).map(this.blocks::get).orElse(null);
  }

  @Override
  public final @Nullable BlockTag getBlockTagByName(String name) {
    return NamespacedKey.tryParse(name)
        .map(key -> key.equals(BLOCK_ENTITIES_TAG)
            ? this.tags.computeIfAbsent(key, this::createBlockEntitiesTag)
            : this.tags.get(key))
        .orElse(null);
  }

  private BlockTag createBlockEntitiesTag(NamespacedKey key) {
    return new BlockTag(key, this.blocks.values().stream()
        .filter(block -> !block.getKey().equals(MOVING_PISTON))
        .filter(block -> block.getBlockStates().stream().anyMatch(BlockStateProperties::isBlockEntity))
        .collect(Collectors.toUnmodifiableSet()));
  }

  @Override
  public final boolean isAir(int blockId) {
    return this.blockStates[blockId].isAir();
  }

  @Override
  public final boolean isFluid(int blockId) {
    return this.blockStates[blockId].isFluid();
  }

  @Override
  public final boolean isLava(int blockId) {
    return this.blockStates[blockId].isLava();
  }

  @Override
  public final boolean isOccluding(int blockId) {
    return this.blockStates[blockId].isOccluding();
  }

  @Override
  public final boolean isBlockEntity(int blockId) {
    return this.blockStates[blockId].isBlockEntity();
  }
}
