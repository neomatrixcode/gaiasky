/*******************************************************************************
 * Copyright 2012 tsagrista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package gaiasky.util.gdx.contrib.postprocess.effects;

import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.filters.DepthBufferFilter;
import gaiasky.util.gdx.contrib.utils.GaiaSkyFrameBuffer;

/**
 * Debug effect. Renders the contents of the depth texture attachment.
 */
public final class DepthBuffer extends PostProcessorEffect {
    private DepthBufferFilter filter;

    /** Creates the effect */
    public DepthBuffer() {
        filter = new DepthBufferFilter();
    }

    @Override
    public void dispose() {
        if (filter != null) {
            filter.dispose();
            filter = null;
        }
    }

    @Override
    public void rebind() {
        filter.rebind();
    }

    @Override
    public void render(FrameBuffer src, FrameBuffer dest, GaiaSkyFrameBuffer main) {
        restoreViewport(dest);
        // Get depth buffer texture from main frame buffer
        filter.setDepthTexture(main.getDepthBufferTexture());
        // Set input, output and render
        filter.setInput(src).setOutput(dest).render();
    }
}
