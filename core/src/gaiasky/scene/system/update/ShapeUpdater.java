package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import gaiasky.GaiaSky;
import gaiasky.scene.Mapper;
import gaiasky.scene.entity.EntityUtils;
import gaiasky.scene.entity.LightingUtils;

import java.util.Locale;

public class ShapeUpdater extends AbstractUpdateSystem {

    private final Vector3 F31 = new Vector3();

    public ShapeUpdater(Family family, int priority) {
        super(family, priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        updateEntity(entity, deltaTime);
    }

    @Override
    public void updateEntity(Entity entity, float deltaTime) {
        var body = Mapper.body.get(entity);
        var graph = Mapper.graph.get(entity);
        var shape = Mapper.shape.get(entity);
        var model = Mapper.model.get(entity);
        var coord = Mapper.coordinates.get(entity);
        var transform = Mapper.transform.get(entity);

        if (!model.model.isStaticLight()) {
            // Update light with global position
            LightingUtils.updateLights(model, body, graph, GaiaSky.instance.cameraManager);
        }

        graph.translation.sub(body.pos);
        if (shape.track != null) {
            // Overwrite position if track object is set.
            EntityUtils.getAbsolutePosition(shape.track.getEntity(), shape.trackName.toLowerCase(Locale.ROOT), body.pos);
        } else if ((coord == null || coord.coordinates == null) && !body.positionSetInScript) {
            // Otherwise, set to zero if the position has not been set in a script, or it has coordinates.
            body.pos.scl(0);
        }
        // Update pos, local transform
        graph.translation.add(body.pos);

        graph.localTransform.idt().translate(graph.translation.put(F31)).scl(body.size);
        if (transform.matrixf != null) {
            graph.localTransform.mul(transform.matrixf);
        }

    }
}
