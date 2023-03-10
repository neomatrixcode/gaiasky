/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.util.i18n.I18n;
import gaiasky.util.scene2d.OwnLabel;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Only for frame output mode, it displays the current time.
 */
public class RenderGui extends AbstractGui {
    protected Label time;
    protected Table mainTable;

    protected MessagesInterface messagesInterface;

    protected DateTimeFormatter df;

    public RenderGui(final Skin skin, final Graphics graphics, final Float unitsPerPixel) {
        super(graphics, unitsPerPixel);
        this.skin = skin;
    }

    @Override
    public void initialize(AssetManager assetManager, SpriteBatch sb) {
        ScreenViewport vp = new ScreenViewport();
        vp.setUnitsPerPixel(unitsPerPixel);
        stage = new Stage(vp, sb);
        df = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withLocale(I18n.locale).withZone(ZoneOffset.UTC);
    }

    @Override
    public void doneLoading(AssetManager assetManager) {
        mainTable = new Table(skin);
        time = new OwnLabel("", skin, "default");
        mainTable.add(time);
        mainTable.setFillParent(true);
        mainTable.right().bottom();
        mainTable.pad(5);

        // MESSAGES INTERFACE - LOW CENTER
        messagesInterface = new MessagesInterface(skin, lock);
        messagesInterface.setFillParent(true);
        messagesInterface.left().bottom();
        messagesInterface.pad(0, 300, 150, 0);

        // Add to GUI
        rebuildGui();

        EventManager.instance.subscribe(this, Event.TIME_CHANGE_INFO);
    }

    protected void rebuildGui() {
        if (stage != null) {
            stage.clear();
            stage.addActor(mainTable);
            stage.addActor(messagesInterface);
        }
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        synchronized (lock) {
            switch (event) {
            case TIME_CHANGE_INFO:
                time.setText(df.format((Instant) data[0]));
                break;
            default:
                break;
            }
        }
    }
}
