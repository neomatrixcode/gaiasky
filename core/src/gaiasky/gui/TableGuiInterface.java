/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public abstract class TableGuiInterface extends Table implements IGuiInterface {
    protected TableGuiInterface(Skin skin) {
        super(skin);
    }

    @Override
    public boolean isOn() {
        return hasParent() && isVisible();
    }
}
