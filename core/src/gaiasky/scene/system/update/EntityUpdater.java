package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.scene.component.GraphNode;

/**
 * Defines the interface for all entity update systems.
 * Entity update systems are assumed to be running after the
 * scene graph update has taken place. The scene graph update
 * traverses the {@link GraphNode} components, which compose
 * the scene graph tree, and updates their positions depth-first.
 */
public interface EntityUpdater {

    /**
     * Returns the family for this updater.
     *
     * @return The family.
     */
    Family getFamily();

    /**
     * Updates the entity.
     *
     * @param entity    The entity to update.
     * @param deltaTime The delta time since last frame.
     */
    void updateEntity(Entity entity, float deltaTime);
}
