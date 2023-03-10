/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.contrib.postprocess;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.ByteBuffer;

/**
 * Provides a simple mechanism to query OpenGL pipeline states. Note: state queries are costly and stall the pipeline, especially
 * on mobile devices!
 *
 * @author bmanuel
 */
public final class PipelineState implements Disposable {

    private final ByteBuffer byteBuffer;

    protected PipelineState() {
        byteBuffer = BufferUtils.newByteBuffer(32);
    }

    public boolean isEnabled(int pname) {
        boolean ret = false;

        switch (pname) {
        case GL20.GL_BLEND:
            Gdx.gl20.glGetBooleanv(GL20.GL_BLEND, byteBuffer);
            ret = (byteBuffer.get() == 1);
            byteBuffer.clear();
            break;
        default:
            ret = false;
        }

        return ret;
    }

    @Override
    public void dispose() {
    }
}
