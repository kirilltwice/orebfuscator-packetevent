package net.imprex.orebfuscator.compatibility;

import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

public interface CompatibilityScheduler {

  void runForPlayer(Player player, Runnable runnable);

  void runForPlayerTimer(Player player, long periodTicks, BooleanSupplier task);

  void runAsyncNow(Runnable runnable);

  void cancelTasks();
}
