package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.GaiaSky;
import gaiasky.scene.Mapper;

public class AxesUpdater extends AbstractUpdateSystem {
    public static final double LINE_SIZE_RAD = Math.tan(Math.toRadians(2.9));

    public AxesUpdater(Family family, int priority) {
        super(family, priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        updateEntity(entity, deltaTime);
    }

    @Override
    public void updateEntity(Entity entity, float deltaTime) {
        var body = Mapper.body.get(entity);
        var axis = Mapper.axis.get(entity);

        var camera = GaiaSky.instance.getICamera();

        body.distToCamera = (float) camera.getPos().lend();
        body.size = (float) (LINE_SIZE_RAD * body.distToCamera) * camera.getFovFactor();

        axis.o.set(camera.getInversePos());
        axis.x.set(axis.b0).scl(body.size).add(axis.o);
        axis.y.set(axis.b1).scl(body.size).add(axis.o);
        axis.z.set(axis.b2).scl(body.size).add(axis.o);

    }
}
