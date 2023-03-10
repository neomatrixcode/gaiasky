/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.api;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import gaiasky.util.Logger;
import gaiasky.util.gdx.contrib.postprocess.PostProcessor;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface IPostProcessor extends Disposable {
    String DEFAULT_KEY = "%default%";

    void initialize(AssetManager manager);

    void doneLoading(AssetManager manager);

    PostProcessBean getPostProcessBean(RenderType type);

    void resize(int width, int height, int targetWidth, int targetHeight);

    void resizeImmediate(int width, int height, int targetWidth, int targetHeight);

    boolean isLightScatterEnabled();

    enum RenderType {
        screen(0),
        screenshot(1),
        frame(2);

        public int index;

        RenderType(int index) {
            this.index = index;
        }

    }

    class PostProcessBean {
        protected static Logger.Log logger = Logger.getLogger(PostProcessBean.class);

        public PostProcessor pp;
        public Map<Class<? extends PostProcessorEffect>, Map<String, PostProcessorEffect>> effects = new HashMap<>();

        /**
         * Adds a new effect to the post processor with the default key
         *
         * @param effect The effect
         */
        public void set(PostProcessorEffect effect) {
            addEffect(DEFAULT_KEY, effect);
        }

        /**
         * Gets the effect of the given class with the default key
         *
         * @param clazz The class
         */
        public PostProcessorEffect get(Class<? extends PostProcessorEffect> clazz) {
            return get(DEFAULT_KEY, clazz);
        }

        /**
         * Sets the given singleton effect to the post processor. This replaces any previous effect of the same type.
         *
         * @param key    The key
         * @param effect The effect
         */
        public void set(String key, PostProcessorEffect effect) {
            addEffect(key, effect);
        }

        /**
         * Adds a new post-processing effect to this post-processor
         *
         * @param key    The key to use
         * @param effect The effect
         */
        private void addEffect(String key, PostProcessorEffect effect) {
            if (effects != null) {
                Map<String, PostProcessorEffect> l = effects.get(effect.getClass());
                if (l != null) {
                    l.put(key, effect);
                } else {
                    l = new HashMap<>();
                    l.put(key, effect);
                    effects.put(effect.getClass(), l);
                }
                pp.addEffect(effect);
            } else {
                logger.error("Effects list not initialized!");
            }
        }

        /**
         * Gets the first effect of the given type
         *
         * @param clazz The class
         *
         * @return The effect
         */
        public PostProcessorEffect get(String key, Class<? extends PostProcessorEffect> clazz) {
            Map<String, PostProcessorEffect> l = effects.get(clazz);
            if (l != null) {
                return l.get(key);
            }
            return null;
        }

        /**
         * Gets all effects of the given type
         *
         * @param clazz The class
         *
         * @return The map of effects
         */
        public Map<String, PostProcessorEffect> getAll(Class<? extends PostProcessorEffect> clazz) {
            return effects.get(clazz);
        }

        /**
         * Removes all effects from the given class
         *
         * @param clazz The class
         */
        public void remove(Class<? extends PostProcessorEffect> clazz) {
            Map<String, PostProcessorEffect> l = getAll(clazz);
            if (l != null) {
                l.forEach((key, ppe) -> {
                    ppe.setEnabled(false);
                    pp.removeEffect(ppe);
                });
                l.clear();
                effects.remove(clazz);
            }
        }

        /**
         * Removes the keyed effect from the given class
         *
         * @param key   The key
         * @param clazz The class
         */
        public void remove(String key, Class<? extends PostProcessorEffect> clazz) {
            Map<String, PostProcessorEffect> l = getAll(clazz);
            if (l != null) {
                if (l.containsKey(key)) {
                    PostProcessorEffect ppe = l.get(key);
                    ppe.setEnabled(false);
                    pp.removeEffect(ppe);
                }
                l.remove(key);
            }
        }

        public boolean capture() {
            return pp.capture();
        }

        public boolean captureNoClear() {
            return pp.captureNoClear();
        }

        public void render() {
            pp.render();
        }

        public FrameBuffer captureEnd() {
            return pp.captureEnd();
        }

        public void render(FrameBuffer destination) {
            pp.render(destination);
        }

        public void dispose(boolean cleanAllBuffers) {
            if (pp != null) {
                pp.dispose(cleanAllBuffers);
                if (effects != null) {
                    Set<Class<? extends PostProcessorEffect>> keys = effects.keySet();
                    for (Class<? extends PostProcessorEffect> key : keys) {
                        Map<String, PostProcessorEffect> l = effects.get(key);
                        if (l != null) {
                            for (String k : l.keySet()) {
                                if (l.get(k) != null)
                                    l.get(k).dispose();
                            }
                        }
                    }
                }
            }
        }

        public void dispose() {
            dispose(true);
        }

    }
}
