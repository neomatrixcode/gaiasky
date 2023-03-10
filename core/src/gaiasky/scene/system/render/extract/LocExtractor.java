package gaiasky.scene.system.render.extract;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.render.RenderGroup;
import gaiasky.scene.Mapper;

public class LocExtractor extends AbstractExtractSystem {

    public LocExtractor(Family family, int priority) {
        super(family, priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var base = Mapper.base.get(entity);
        if (mustRender(base)) {
            addToRender(Mapper.render.get(entity), RenderGroup.FONT_LABEL);
        }
    }

}
