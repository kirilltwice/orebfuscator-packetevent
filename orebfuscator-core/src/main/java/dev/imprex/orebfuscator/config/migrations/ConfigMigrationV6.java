package dev.imprex.orebfuscator.config.migrations;

import dev.imprex.orebfuscator.config.yaml.ConfigurationSection;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ConfigMigrationV6 implements ConfigMigration {

  private static final String BLOCK_ENTITIES_TAG = "tag(orebfuscator:block_entities)";

  @Override
  public int sourceVersion() {
    return 6;
  }

  @Override
  public ConfigurationSection migrate(ConfigurationSection root) {
    ConfigurationSection proximity = root.getSection("proximity");
    if (proximity == null) {
      return root;
    }

    for (ConfigurationSection config : proximity.getSubSections()) {
      config.set("rayCastCheck.enabled", true);
      config.set("entityHider.enabled", true);

      ConfigurationSection hiddenBlocks = config.getSection("hiddenBlocks");
      if (hiddenBlocks == null) {
        hiddenBlocks = config.createSection("hiddenBlocks");
      }
      if (!hiddenBlocks.isSection(BLOCK_ENTITIES_TAG)) {
        hiddenBlocks.createSection(BLOCK_ENTITIES_TAG);
      }
    }

    return root;
  }
}
