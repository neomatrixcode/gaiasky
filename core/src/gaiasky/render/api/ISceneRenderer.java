package gaiasky.render.api;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.render.ComponentTypes;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.RenderingContext;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.system.render.pass.LightGlowPass;

import java.util.List;

/**
 * Defines the interface for scene renderers.
 */
public interface ISceneRenderer {

    /**
     * Renders the scene.
     *
     * @param camera        The camera to use.
     * @param t             The time in seconds since the start.
     * @param renderContext The render context.
     */
    void renderScene(ICamera camera, double t, RenderingContext renderContext);


    /**
     * Initializes the renderer, sending all the necessary assets to the manager
     * for loading.
     *
     * @param manager The asset manager.
     */
    void initialize(AssetManager manager);

    /**
     * Actually initializes all the clockwork of this renderer using the assets
     * in the given manager.
     *
     * @param manager The asset manager.
     */
    void doneLoading(AssetManager manager);

    /**
     * Checks if a given component type is on.
     *
     * @param comp The component.
     *
     * @return Whether the component is on.
     */
    boolean isOn(ComponentType comp);

    /**
     * Checks if the component types are all on.
     *
     * @param comp The components.
     *
     * @return Whether the components are all on.
     */
    boolean allOn(ComponentTypes comp);

    /**
     * Gets the current render process.
     *
     * @return The render mode.
     */
    IRenderMode getRenderProcess();

    /**
     * Returns the post-processing glow frame buffer.
     *
     * @return The glow frame buffer.
     */
    FrameBuffer getGlowFrameBuffer();

    /** Returns he render lists of this renderer. **/
    List<List<IRenderable>> getRenderLists();

    /** Gets the light glow pass object. **/
    LightGlowPass getLightGlowPass();
}
