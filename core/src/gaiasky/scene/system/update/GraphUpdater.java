package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.scene.Mapper;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.component.*;
import gaiasky.scene.component.tag.TagNoProcess;
import gaiasky.scene.view.SpacecraftView;
import gaiasky.util.Logger;
import gaiasky.util.Nature;
import gaiasky.util.Settings;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.math.MathUtilsDouble;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;
import net.jafama.FastMath;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes entities in a scene graph, which have a {@link GraphRoot}
 * component. Generally, this should be a single entity unless
 * we have more than one scene graph.
 */
public class GraphUpdater extends AbstractUpdateSystem {
    private static final Logger.Log logger = Logger.getLogger(GraphUpdater.class);
    private final ITimeFrameProvider time;
    int processed = 0, lastProcessed;
    private ICamera camera;
    private Vector3d D31;
    private Vector3b B31;
    private SpacecraftView view;

    /**
     * Instantiates a system that will iterate over the entities described by the Family.
     *
     * @param family The family of entities iterated over in this System. In this case, it should be just one ({@link GraphRoot}.
     */
    public GraphUpdater(Family family, int priority, ITimeFrameProvider time) {
        super(family, priority);
        this.time = time;
        this.D31 = new Vector3d();
        this.B31 = new Vector3b();
        this.view = new SpacecraftView();
    }

    public void setCamera(ICamera camera) {
        this.camera = camera;
    }

    protected void processEntity(Entity entity, float deltaTime) {
        processed = 0;
        updateEntity(entity, deltaTime);
        if (lastProcessed != processed) {
            logger.debug("Number of nodes (new): " + processed);
            lastProcessed = processed;
            printTree(entity, " ", 0, new AtomicInteger(1));
        }
    }

    @Override
    public void updateEntity(Entity entity, float deltaTime) {
        // This runs the root node
        var root = entity.getComponent(GraphNode.class);

        root.translation.set(camera.getInversePos());
        update(entity, time, null, 1);
    }

    public void printTree(Entity entity, String tab, int level, AtomicInteger count) {
        var graph = Mapper.graph.get(entity);

        if (graph.mustUpdateFunction == null ||
                graph.mustUpdateFunction.apply(this, entity, graph)) {

            var base = Mapper.base.get(entity);

            logger.debug(count.getAndIncrement() + "|" + level + ":" + tab + base.getName()
                    + " (" + (graph.children != null ? graph.children.size : 0) + ")"
                    + (base.archetype != null ? " [" + base.archetype.getName() + "]" : ""));

            boolean processChildren = !Mapper.tagNoProcessChildren.has(entity);
            if (processChildren && graph.children != null) {
                // Go down a level
                for (int i = 0; i < graph.children.size; i++) {
                    Entity child = graph.children.get(i);
                    printTree(child, tab + "  ", level + 1, count);
                }
            }
        }
    }

    public void update(Entity entity, ITimeFrameProvider time, final Vector3b parentTransform, float opacity) {
        processed++;
        var graph = Mapper.graph.get(entity);

        if (graph.mustUpdateFunction == null ||
                graph.mustUpdateFunction.apply(this, entity, graph)) {

            var base = Mapper.base.get(entity);
            var body = Mapper.body.get(entity);
            var coordinates = Mapper.coordinates.get(entity);
            var fade = Mapper.fade.get(entity);

            // Update local position here
            if (time.getHdiff() != 0 && coordinates != null && coordinates.coordinates != null) {
                var rotation = Mapper.rotation.get(entity);
                // Load this object's equatorial cartesian coordinates into pos
                coordinates.timeOverflow = coordinates.coordinates.getEquatorialCartesianCoordinates(time.getTime(), body.pos) == null;

                // Update the spherical position
                gaiasky.util.coord.Coordinates.cartesianToSpherical(body.pos, D31);
                body.posSph.set((float) (Nature.TO_DEG * D31.x), (float) (Nature.TO_DEG * D31.y));

                // Update angle
                if (rotation != null && rotation.rc != null)
                    rotation.rc.update(time);
            }

            graph.translation.set(parentTransform);
            // Update local values.
            if (graph.positionUpdaterConsumer != null) {
                graph.positionUpdaterConsumer.apply(this, entity, graph);
            }
            graph.translation.add(body.pos);

            // Update opacity
            if (fade != null && (fade.fadeIn != null || fade.fadeOut != null)) {
                // If the bottom part of our fade in is mapped to a value
                // greater than zero, we do not use the parent opacity; the
                // children's fadeInMap attribute takes precedence.
                if (fade.fadeInMap == null || fade.fadeInMap.x <= 0) {
                    base.opacity = opacity;
                } else {
                    base.opacity = 1;
                }
                updateFadeDistance(body, fade);
                updateFadeOpacity(base, fade);
            } else {
                base.opacity = opacity;
            }
            base.opacity *= base.getVisibilityOpacityFactor();

            // Apply proper motion if needed
            if (Mapper.pm.has(entity)) {
                var pm = Mapper.pm.get(entity);
                Vector3d pmv = D31.set(pm.pm).scl(AstroUtils.getMsSince(time.getTime(), pm.epochJd) * Nature.MS_TO_Y);
                graph.translation.add(pmv);
            }

            // Update supporting attributes
            body.distToCamera = graph.translation.lend();
            if (Mapper.extra.has(entity)) {
                // Particles have a special algorithm for the solid angles.
                body.solidAngle = (Mapper.extra.get(entity).radius / body.distToCamera);
                body.solidAngleApparent = body.solidAngle * Settings.settings.scene.star.brightness / camera.getFovFactor();
            } else {
                // Regular objects.
                // Take into account size of model objects.
                var model = Mapper.model.get(entity);
                double modelSize = model != null ? model.modelSize : 1;
                body.solidAngle = FastMath.atan(body.size * modelSize / body.distToCamera);
                body.solidAngleApparent = body.solidAngle / camera.getFovFactor();
            }

            var ds = Mapper.datasetDescription.get(entity);
            // Some elements (sets, octrees) process their own children.
            boolean processChildren = !(Mapper.tagNoProcessChildren.has(entity) || (ds != null && !GaiaSky.instance.isOn(base.ct)));
            if (processChildren && graph.children != null && opacity > 0) {
                // Go down a level
                for (int i = 0; i < graph.children.size; i++) {
                    Entity child = graph.children.get(i);
                    update(child, time, graph.translation, getChildrenOpacity(entity, base, fade, opacity));
                }
            }
        }
    }

    private float getChildrenOpacity(Entity entity, Base base, Fade fade, float opacity) {
        if (Mapper.billboardSet.has(entity)) {
            return 1 - base.opacity;
        } else if (fade != null) {
            return base.opacity;
        } else {
            return opacity;
        }
    }

    private void updateFadeDistance(Body body, Fade fade) {
        if (fade.fadePositionObject != null) {
            fade.currentDistance = Mapper.body.get(fade.fadePositionObject).distToCamera;
        } else if (fade.fadePosition != null) {
            fade.currentDistance = D31.set(fade.fadePosition).sub(camera.getPos()).len() * camera.getFovFactor();
        } else {
            fade.currentDistance = D31.set(body.pos).sub(camera.getPos()).len() * camera.getFovFactor();
        }
        body.distToCamera = fade.fadePositionObject == null ? body.pos.dst(camera.getPos(), B31).doubleValue() : Mapper.body.get(fade.fadePositionObject).distToCamera;
    }

    private void updateFadeOpacity(Base base, Fade fade) {
        if (fade.fadeIn != null) {
            base.opacity *= MathUtilsDouble.lint(fade.currentDistance, fade.fadeIn.x, fade.fadeIn.y, fade.fadeInMap.x, fade.fadeInMap.y);
        }
        if (fade.fadeOut != null) {
            base.opacity *= MathUtilsDouble.lint(fade.currentDistance, fade.fadeOut.x, fade.fadeOut.y, fade.fadeOutMap.x, fade.fadeOutMap.y);
        }
    }

    protected float getVisibilityOpacityFactor(Base base) {
        long msSinceStateChange = msSinceStateChange(base);

        // Fast track
        if (msSinceStateChange > Settings.settings.scene.fadeMs)
            return base.visible ? 1 : 0;

        // Fading
        float fadeOpacity = MathUtilsDouble.lint(msSinceStateChange, 0, Settings.settings.scene.fadeMs, 0, 1);
        if (!base.visible) {
            fadeOpacity = 1 - fadeOpacity;
        }
        return fadeOpacity;
    }

    protected long msSinceStateChange(Base base) {
        return (long) (GaiaSky.instance.getT() * 1000f) - base.lastStateChangeTimeMs;
    }

    public boolean mustUpdateLoc(Entity entity, GraphNode graph) {
        var parentBody = Mapper.body.get(graph.parent);
        var parentSa = Mapper.sa.get(graph.parent);

        boolean update = parentBody.solidAngle > parentSa.thresholdQuad * 30f;
        if (update) {
            entity.remove(TagNoProcess.class);
        } else {
            entity.add(getEngine().createComponent(TagNoProcess.class));
        }
        return update;
    }

    public void updateSpacecraft(Entity entity, GraphNode graph) {
        view.setEntity(entity);
        var engine = view.engine;

        if (engine.yawv != 0 || engine.pitchv != 0 || engine.rollv != 0 || engine.vel.len2() != 0 || engine.render) {
            var coord = Mapper.coordinates.get(entity);

            // We use the simulation time for the integration
            // Poll keys
            if (camera.getMode().isSpacecraft()) {
                view.pollKeys(Gdx.graphics.getDeltaTime());
            }

            double dt = time.getDt();

            // POSITION
            coord.coordinates.getEquatorialCartesianCoordinates(time.getTime(), view.body.pos);

            if (engine.leveling) {
                // No velocity, we just stop Euler angle motions
                if (engine.yawv != 0) {
                    engine.yawp = -Math.signum(engine.yawv) * MathUtilsDouble.clamp(Math.abs(engine.yawv), 0, 1);
                }
                if (engine.pitchv != 0) {
                    engine.pitchp = -Math.signum(engine.pitchv) * MathUtilsDouble.clamp(Math.abs(engine.pitchv), 0, 1);
                }
                if (engine.rollv != 0) {
                    engine.rollp = -Math.signum(engine.rollv) * MathUtilsDouble.clamp(Math.abs(engine.rollv), 0, 1);
                }
                if (Math.abs(engine.yawv) < 1e-3 && Math.abs(engine.pitchv) < 1e-3 && Math.abs(engine.rollv) < 1e-3) {
                    engine.setYawPower(0);
                    engine.setPitchPower(0);
                    engine.setRollPower(0);

                    engine.yawv = 0;
                    engine.pitchv = 0;
                    engine.rollv = 0;
                    EventManager.publish(Event.SPACECRAFT_STABILISE_CMD, this, false);
                }
            }

            double rollDiff = view.computeDirectionUp(dt, engine.dirup);

            double len = engine.direction.len();
            engine.pitch = Math.asin(engine.direction.y / len);
            engine.yaw = Math.atan2(engine.direction.z, engine.direction.x);
            engine.roll += rollDiff;

            engine.pitch = Math.toDegrees(engine.pitch);
            engine.yaw = Math.toDegrees(engine.yaw);
        }
        // Update float vectors
        Vector3b camPos = B31.set(view.body.pos).add(camera.getInversePos());
        camPos.put(engine.posf);
        engine.direction.put(engine.directionf);
        engine.up.put(engine.upf);
    }

}
