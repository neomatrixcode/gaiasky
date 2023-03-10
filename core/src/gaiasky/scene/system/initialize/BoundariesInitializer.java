package gaiasky.scene.system.initialize;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.scene.Mapper;
import gaiasky.scene.system.render.draw.line.LineEntityRenderSystem;

public class BoundariesInitializer extends AbstractInitSystem {
    public BoundariesInitializer(boolean setUp, Family family, int priority) {
        super(setUp, family, priority);
    }

    @Override
    public void initializeEntity(Entity entity) {
        var line = Mapper.line.get(entity);
        // Lines.
        line.lineWidth = 1;
        line.renderConsumer = LineEntityRenderSystem::renderConstellationBoundaries;
    }

    @Override
    public void setUpEntity(Entity entity) {

    }
}
