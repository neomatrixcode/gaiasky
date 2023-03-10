/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.contrib.postprocess.effects;

import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;

public abstract class Antialiasing extends PostProcessorEffect {

    public abstract void setViewportSize(int width, int height);

    public abstract void updateQuality(int quality);
}
