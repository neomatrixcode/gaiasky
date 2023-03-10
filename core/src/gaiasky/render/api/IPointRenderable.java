/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.api;

/**
 * Interface to be implemented by those entities that can be rendered
 * as a single point, floated by the camera position in the CPU
 */
public interface IPointRenderable extends IRenderable {

    void blend();

    void depth();

}
