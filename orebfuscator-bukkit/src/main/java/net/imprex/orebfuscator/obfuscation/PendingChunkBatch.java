package net.imprex.orebfuscator.obfuscation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class PendingChunkBatch {

  private final List<CompletableFuture<Void>> futures = new ArrayList<>();
  private int completed = 0;
  private boolean finished = false;

  void addChunk(CompletableFuture<Void> future) {
    if (!this.finished) {
      this.futures.add(future);
    }
  }

  void finish() {
    this.finished = true;
  }

  boolean isDone() {
    if (!this.finished) {
      return false;
    }

    while (this.completed < this.futures.size() && this.futures.get(this.completed).isDone()) {
      this.completed++;
    }

    return this.completed == this.futures.size();
  }
}
