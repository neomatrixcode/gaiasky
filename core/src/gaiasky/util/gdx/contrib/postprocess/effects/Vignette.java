/*******************************************************************************
 * Copyright 2012 bmanuel
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

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.filters.Vignetting;
import gaiasky.util.gdx.contrib.utils.GaiaSkyFrameBuffer;

public final class Vignette extends PostProcessorEffect {
    private final Vignetting vignetting;
    private final boolean controlSaturation;
    private final float oneOnW;
    private final float oneOnH;

    public Vignette(int viewportWidth, int viewportHeight, boolean controlSaturation) {
        this.controlSaturation = controlSaturation;
        oneOnW = 1f / (float) viewportWidth;
        oneOnH = 1f / (float) viewportHeight;
        vignetting = new Vignetting(controlSaturation);
    }

    @Override
    public void dispose() {
        vignetting.dispose();
    }

    public boolean doesSaturationControl() {
        return controlSaturation;
    }

    public void setCoords(float x, float y) {
        vignetting.setCoords(x, y);
    }

    public void setX(float x) {
        vignetting.setX(x);
    }

    public void setY(float y) {
        vignetting.setY(y);
    }

    public void setLutTexture(Texture texture) {
        vignetting.setLut(texture);
    }

    public void setLutIndexVal(int index, int value) {
        vignetting.setLutIndexVal(index, value);
    }

    public void setLutIndexOffset(float value) {
        vignetting.setLutIndexOffset(value);
    }

    /** Specify the center, in screen coordinates. */
    public void setCenter(float x, float y) {
        vignetting.setCenter(x * oneOnW, 1f - y * oneOnH);
    }

    public float getIntensity() {
        return vignetting.getIntensity();
    }

    public void setIntensity(float intensity) {
        vignetting.setIntensity(intensity);
    }

    public float getLutIntensity() {
        return vignetting.getLutIntensity();
    }

    public void setLutIntensity(float value) {
        vignetting.setLutIntensity(value);
    }

    public int getLutIndexVal(int index) {
        return vignetting.getLutIndexVal(index);
    }

    public Texture getLut() {
        return vignetting.getLut();
    }

    public float getCenterX() {
        return vignetting.getCenterX();
    }

    public float getCenterY() {
        return vignetting.getCenterY();
    }

    public float getCoordsX() {
        return vignetting.getX();
    }

    public float getCoordsY() {
        return vignetting.getY();
    }

    public float getSaturation() {
        return vignetting.getSaturation();
    }

    public void setSaturation(float saturation) {
        vignetting.setSaturation(saturation);
    }

    public float getSaturationMul() {
        return vignetting.getSaturationMul();
    }

    public void setSaturationMul(float saturationMul) {
        vignetting.setSaturationMul(saturationMul);
    }

    public boolean isGradientMappingEnabled() {
        return vignetting.isGradientMappingEnabled();
    }

    @Override
    public void rebind() {
        vignetting.rebind();
    }

    @Override
    public void render(FrameBuffer src, FrameBuffer dest, GaiaSkyFrameBuffer main) {
        restoreViewport(dest);
        vignetting.setInput(src).setOutput(dest).render();
    }

}
