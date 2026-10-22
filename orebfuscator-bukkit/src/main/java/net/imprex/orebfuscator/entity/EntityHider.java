package net.imprex.orebfuscator.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import dev.imprex.orebfuscator.PermissionRequirements;
import dev.imprex.orebfuscator.config.OrebfuscatorConfig;
import dev.imprex.orebfuscator.config.api.ProximityConfig;
import dev.imprex.orebfuscator.interop.RegistryAccessor;
import dev.imprex.orebfuscator.logging.OfcLogger;
import dev.imprex.orebfuscator.proximity.ProximityRayCaster;
import dev.imprex.orebfuscator.util.EntityPose;
import dev.imprex.orebfuscator.util.concurrent.OrebfuscatorExecutor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.OrebfuscatorNms;
import net.imprex.orebfuscator.iterop.BukkitPlayerAccessor;
import net.imprex.orebfuscator.iterop.BukkitWorldAccessor;
import org.bukkit.World;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.ComplexLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class EntityHider {

  private static final int[] EMPTY = new int[0];

  private static final double BOUNDING_BOX_EXPANSION = 0.5;
  private static final double ALWAYS_VISIBLE_DISTANCE_SQUARED = 4 * 4;

  private final OrebfuscatorConfig config;
  private final RegistryAccessor registry;
  private final OrebfuscatorExecutor executor;
  private final BukkitPlayerAccessor player;

  private final Set<Integer> tracked = ConcurrentHashMap.newKeySet();
  private volatile int[] hidden = EMPTY;

  private volatile @Nullable Snapshot pendingResult;
  private volatile boolean computing = false;

  public EntityHider(Orebfuscator orebfuscator, BukkitPlayerAccessor player) {
    this.config = orebfuscator.config();
    this.registry = orebfuscator.registry();
    this.executor = orebfuscator.executor();
    this.player = player;
  }

  public void onSpawn(int entityId) {
    this.tracked.add(entityId);
  }

  public void onDestroy(int entityId) {
    this.tracked.remove(entityId);
  }

  public boolean hasHidden() {
    return this.hidden.length != 0;
  }

  public boolean isHidden(int entityId) {
    int[] hidden = this.hidden;
    return hidden.length != 0 && Arrays.binarySearch(hidden, entityId) >= 0;
  }

  void tick() {
    Snapshot result = this.pendingResult;
    if (result != null) {
      this.pendingResult = null;
      this.apply(result);
    }

    if (this.computing) {
      return;
    }

    BukkitWorldAccessor world = this.player.world();
    ProximityConfig proximityConfig = world.config().proximity();
    if (!this.isEnabled(proximityConfig)) {
      this.showAll();
      return;
    }

    if (!this.player.isAlive() || this.tracked.isEmpty()) {
      return;
    }

    Snapshot snapshot = this.createSnapshot(world);
    if (snapshot.size == 0) {
      return;
    }

    this.computing = true;
    CompletableFuture.runAsync(() -> this.compute(snapshot), this.executor).whenComplete((v, throwable) -> {
      if (throwable != null) {
        OfcLogger.error("An error occurred while hiding entities", throwable);
      } else {
        this.pendingResult = snapshot;
      }
      this.computing = false;
    });
  }

  private boolean isEnabled(@Nullable ProximityConfig proximityConfig) {
    if (proximityConfig == null || !proximityConfig.isEnabled() || !proximityConfig.entityHiderEnabled()) {
      return false;
    }

    if (this.player.hasPermission(PermissionRequirements.BYPASS)) {
      return false;
    }

    return !(this.player.isSpectator() && this.config.general().ignoreSpectator());
  }

  private Snapshot createSnapshot(BukkitWorldAccessor world) {
    Player bukkitPlayer = this.player.player();
    World bukkitWorld = world.world;
    Entity playerRoot = rootVehicle(bukkitPlayer);
    EntityPose eye = this.player.eyePose();

    Snapshot snapshot = new Snapshot(world, eye, this.tracked.size());
    for (Integer entityId : this.tracked) {
      Entity entity = OrebfuscatorNms.getEntity(bukkitWorld, entityId);
      if (entity == null) {
        continue;
      }

      Entity root = rootVehicle(entity);
      BoundingBox box = entity.getBoundingBox();

      double dx = box.getCenterX() - eye.x();
      double dy = box.getCenterY() - eye.y();
      double dz = box.getCenterZ() - eye.z();

      boolean alwaysVisible = root == playerRoot
          || dx * dx + dy * dy + dz * dz <= ALWAYS_VISIBLE_DISTANCE_SQUARED
          || isAlwaysVisible(entity);

      snapshot.add(entityId, root.getEntityId(), box, alwaysVisible);
    }

    return snapshot;
  }

  private static Entity rootVehicle(Entity entity) {
    Entity vehicle;
    while ((vehicle = entity.getVehicle()) != null) {
      entity = vehicle;
    }
    return entity;
  }

  private static boolean isAlwaysVisible(Entity entity) {
    if (entity instanceof Projectile
        || entity instanceof LightningStrike
        || entity instanceof ComplexLivingEntity
        || entity instanceof ComplexEntityPart) {
      return true;
    }

    return entity.isGlowing()
        || (entity instanceof LivingEntity living && living.hasPotionEffect(PotionEffectType.GLOWING));
  }

  private void compute(Snapshot snapshot) {
    ProximityRayCaster rayCaster = new ProximityRayCaster(this.registry, snapshot.world);
    Set<Integer> visibleRoots = new HashSet<>();

    for (int i = 0; i < snapshot.size; i++) {
      if (snapshot.visible[i]) {
        visibleRoots.add(snapshot.roots[i]);
      }
    }

    double[] boxes = snapshot.boxes;
    for (int i = 0; i < snapshot.size; i++) {
      if (snapshot.visible[i] || visibleRoots.contains(snapshot.roots[i])) {
        continue;
      }

      int offset = i * 6;
      if (rayCaster.isVisible(snapshot.eye, boxes[offset], boxes[offset + 1], boxes[offset + 2],
          boxes[offset + 3], boxes[offset + 4], boxes[offset + 5])) {
        visibleRoots.add(snapshot.roots[i]);
      }
    }

    for (int i = 0; i < snapshot.size; i++) {
      snapshot.visible[i] = visibleRoots.contains(snapshot.roots[i]);
    }
  }

  private void apply(Snapshot snapshot) {
    BukkitWorldAccessor world = this.player.world();
    if (!world.equals(snapshot.world)) {
      return;
    }

    Set<Integer> hidden = new HashSet<>();
    for (int entityId : this.hidden) {
      if (OrebfuscatorNms.getEntity(world.world, entityId) != null) {
        hidden.add(entityId);
      }
    }

    int[] destroy = new int[snapshot.size];
    int destroyCount = 0;
    int[] show = new int[snapshot.size];
    int showCount = 0;

    for (int i = 0; i < snapshot.size; i++) {
      int entityId = snapshot.ids[i];
      if (snapshot.visible[i]) {
        if (hidden.remove(entityId)) {
          show[showCount++] = entityId;
        }
      } else if (this.tracked.contains(entityId) && hidden.add(entityId)) {
        destroy[destroyCount++] = entityId;
      }
    }

    this.publish(hidden);

    if (destroyCount != 0) {
      PacketEvents.getAPI().getPlayerManager().sendPacketSilently(this.player.player(),
          new WrapperPlayServerDestroyEntities(Arrays.copyOf(destroy, destroyCount)));
    }

    Player bukkitPlayer = this.player.player();
    for (int i = 0; i < showCount; i++) {
      OrebfuscatorNms.resendEntity(bukkitPlayer, show[i]);
    }
  }

  void showAll() {
    int[] hidden = this.hidden;
    if (hidden.length == 0) {
      return;
    }

    this.hidden = EMPTY;
    this.pendingResult = null;

    Player bukkitPlayer = this.player.player();
    for (int entityId : hidden) {
      if (this.tracked.contains(entityId)) {
        OrebfuscatorNms.resendEntity(bukkitPlayer, entityId);
      }
    }
  }

  private void publish(Set<Integer> hidden) {
    if (hidden.isEmpty()) {
      this.hidden = EMPTY;
      return;
    }

    int[] array = new int[hidden.size()];
    int index = 0;
    for (int entityId : hidden) {
      array[index++] = entityId;
    }
    Arrays.sort(array);
    this.hidden = array;
  }

  private static final class Snapshot {

    final BukkitWorldAccessor world;
    final EntityPose eye;

    final int[] ids;
    final int[] roots;
    final double[] boxes;
    final boolean[] visible;
    int size = 0;

    Snapshot(BukkitWorldAccessor world, EntityPose eye, int capacity) {
      this.world = world;
      this.eye = eye;
      this.ids = new int[capacity];
      this.roots = new int[capacity];
      this.boxes = new double[capacity * 6];
      this.visible = new boolean[capacity];
    }

    void add(int entityId, int rootId, BoundingBox box, boolean alwaysVisible) {
      if (this.size == this.ids.length) {
        return;
      }

      int offset = this.size * 6;
      this.boxes[offset] = box.getMinX() - BOUNDING_BOX_EXPANSION;
      this.boxes[offset + 1] = box.getMinY() - BOUNDING_BOX_EXPANSION;
      this.boxes[offset + 2] = box.getMinZ() - BOUNDING_BOX_EXPANSION;
      this.boxes[offset + 3] = box.getMaxX() + BOUNDING_BOX_EXPANSION;
      this.boxes[offset + 4] = box.getMaxY() + BOUNDING_BOX_EXPANSION;
      this.boxes[offset + 5] = box.getMaxZ() + BOUNDING_BOX_EXPANSION;

      this.ids[this.size] = entityId;
      this.roots[this.size] = rootId;
      this.visible[this.size] = alwaysVisible;
      this.size++;
    }
  }
}
