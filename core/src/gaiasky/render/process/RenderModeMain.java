/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.process;

import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.render.api.IPostProcessor.PostProcessBean;
import gaiasky.render.api.IRenderMode;
import gaiasky.render.api.ISceneRenderer;
import gaiasky.scene.camera.ICamera;

/**
 * Normal single-window desktop render mode.
 */
public class RenderModeMain extends RenderModeAbstract implements IRenderMode {

    public RenderModeMain() {
        super();
    }

    @Override
    public void render(ISceneRenderer sgr, ICamera camera, double t, int rw, int rh, int tw, int th, FrameBuffer fb, PostProcessBean ppb) {
        boolean postProcess = postProcessCapture(ppb, fb, rw, rh);

        // Viewport
        extendViewport.setCamera(camera.getCamera());
        extendViewport.setWorldSize(rw, rh);
        extendViewport.setScreenSize(rw, rh);
        extendViewport.apply();

        // Render
        sgr.renderScene(camera, t, rc);

        // Uncomment this to show the shadow map
        //if (GlobalConf.scene.SHADOW_MAPPING) {
        //    float screenSize = 300 * GlobalConf.SCALE_FACTOR;
        //    int s = GlobalConf.scene.SHADOW_MAPPING_RESOLUTION;
        //    float scl = screenSize / (float) s;
        //    // Render shadow map
        //    sb.begin();
        //    for (int i = 0; i < sgr.shadowMapFb.length; i++) {
        //        sb.draw(sgr.shadowMapFb[i].getColorBufferTexture(), 0, 0, 0, 0, s, s, scl, scl, 0f, 0, 0, s, s, false, false);
        //    }
        //    sb.end();
        //}

        // GLFW reports a window size of 0x0 with AMD Graphics on Windows when minimizing
        if (rw > 0 && rh > 0) {
            sendOrientationUpdate(camera.getCamera(), rw, rh);
            postProcessRender(ppb, fb, postProcess, camera, tw, th);
        }

    }

    @Override
    public void resize(int rw, int rh, int tw, int th) {
    }

    @Override
    public void dispose() {
    }

}
