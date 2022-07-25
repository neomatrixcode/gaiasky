package gaiasky.scene.system.initialize;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.Base;
import gaiasky.scene.component.DatasetDescription;
import gaiasky.scene.component.GraphNode;
import gaiasky.scene.component.OrbitElementsSet;
import gaiasky.scene.component.tag.TagSetElement;
import gaiasky.scene.entity.ElementsSetRadio;
import gaiasky.util.CatalogInfo;
import gaiasky.util.CatalogInfo.CatalogInfoSource;

public class ElementsSetInitializer extends AbstractInitSystem {
    public ElementsSetInitializer(boolean setUp, Family family, int priority) {
        super(setUp, family, priority);
    }

    @Override
    public void initializeEntity(Entity entity) {

    }

    @Override
    public void setUpEntity(Entity entity) {
        var base = Mapper.base.get(entity);
        var graph = Mapper.graph.get(entity);
        var set = Mapper.orbitElementsSet.get(entity);
        var desc = Mapper.datasetDescription.get(entity);

        // Check children which need updating every time
        initializeOrbitsWithOrbit(graph, set);

        // Initialize catalog info if not set
        initializeCatalogInfo(entity, base, graph, desc);

        EventManager.instance.subscribe(new ElementsSetRadio(entity, this), Event.GPU_DISPOSE_ORBITAL_ELEMENTS);
    }

    /**
     * Gather the children objects that need to be rendered as an orbit line into a list,
     * for they need to be updated every single frame.
     */
    public void initializeOrbitsWithOrbit(GraphNode graph, OrbitElementsSet set) {
        if (set.alwaysUpdate == null) {
            set.alwaysUpdate = new Array<>();
        } else {
            set.alwaysUpdate.clear();
        }
        if (graph.children != null && graph.children.size > 0) {
            for (Entity e : graph.children) {
                // Add tag to identify them as set elements.
                e.add(new TagSetElement());

                // The orbits need to go to the alwaysUpdate list.
                if (Mapper.trajectory.has(e)) {
                    var trajectory = Mapper.trajectory.get(e);
                    if (!trajectory.onlyBody) {
                        set.alwaysUpdate.add(e);
                    }
                } else {
                    // Not an orbit, always add
                    set.alwaysUpdate.add(e);
                }
            }
        }
    }

    private void initializeCatalogInfo(Entity entity, Base base, GraphNode graph, DatasetDescription desc) {
        if (desc.catalogInfo == null) {
            // Create catalog info and broadcast
            CatalogInfo ci = new CatalogInfo(base.names[0], base.names[0], null, CatalogInfoSource.INTERNAL, 1f, entity);
            ci.nParticles = graph.children != null ? graph.children.size : -1;

            // Insert
            EventManager.publish(Event.CATALOG_ADD, this, ci, false);
        }
    }
}