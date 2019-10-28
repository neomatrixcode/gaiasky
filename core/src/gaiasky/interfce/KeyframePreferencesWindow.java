/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interfce;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import gaiasky.desktop.util.camera.CameraKeyframeManager;
import gaiasky.interfce.beans.ComboBoxBean;
import gaiasky.util.GlobalConf;
import gaiasky.util.I18n;
import gaiasky.util.scene2d.OwnLabel;
import gaiasky.util.scene2d.OwnSelectBox;
import gaiasky.util.scene2d.OwnTextField;
import gaiasky.util.validator.IntValidator;

public class KeyframePreferencesWindow extends GenericDialog {

    OwnSelectBox<ComboBoxBean> posMethod, orientationMethod;
    OwnTextField camrecFps;

    public KeyframePreferencesWindow(Stage stage, Skin skin) {
        super(I18n.txt("gui.keyframes.preferences"), skin, stage);
        setModal(true);

        setAcceptText(I18n.txt("gui.saveprefs"));
        setCancelText(I18n.txt("gui.cancel"));

        buildSuper();
    }

    @Override
    protected void build() {

        ComboBoxBean[] interpolation = new ComboBoxBean[] { new ComboBoxBean(I18n.txt("gui.interpolation.linear"), CameraKeyframeManager.PathType.LINEAR.ordinal()), new ComboBoxBean(I18n.txt("gui.interpolation.catmull"), CameraKeyframeManager.PathType.SPLINE.ordinal()) };

        OwnLabel generalTitle = new OwnLabel(I18n.txt("gui.general"), skin, "hud-header");

        // fps
        OwnLabel camfpsLabel = new OwnLabel(I18n.txt("gui.target.fps"), skin);
        camrecFps = new OwnTextField(Integer.toString(GlobalConf.frame.CAMERA_REC_TARGET_FPS), skin, new IntValidator(1, 200));
        camrecFps.setWidth(150 * GlobalConf.UI_SCALE_FACTOR);

        OwnLabel interpTitle = new OwnLabel(I18n.txt("gui.keyframes.interp"), skin, "hud-header");

        // Camera position
        OwnLabel pos = new OwnLabel(I18n.txt("gui.cam.pos"), skin);
        posMethod = new OwnSelectBox<>(skin);
        posMethod.setItems(interpolation);
        posMethod.setSelectedIndex(GlobalConf.frame.KF_PATH_TYPE_POSITION.ordinal());
        posMethod.setItems(interpolation);
        posMethod.setWidth(150 * GlobalConf.UI_SCALE_FACTOR);

        // Camera orientation
        OwnLabel orientation = new OwnLabel(I18n.txt("gui.cam.orientation"), skin);
        orientationMethod = new OwnSelectBox<>(skin);
        orientationMethod.setItems(interpolation);
        orientationMethod.setSelectedIndex(GlobalConf.frame.KF_PATH_TYPE_ORIENTATION.ordinal());
        orientationMethod.setItems(interpolation);
        orientationMethod.setWidth(150 * GlobalConf.UI_SCALE_FACTOR);

        // Time
        OwnLabel time = new OwnLabel(I18n.txt("gui.time"), skin);
        OwnLabel timeMethod = new OwnLabel(I18n.txt("gui.interpolation.linear"), skin);

        content.add(generalTitle).left().top().colspan(2).padBottom(pad).row();

        content.add(camfpsLabel).left().padRight(pad).padBottom(pad * 3f);
        content.add(camrecFps).left().padBottom(pad * 3f).row();

        content.add(interpTitle).left().top().colspan(2).padBottom(pad).row();

        content.add(pos).left().padRight(pad).padBottom(pad);
        content.add(posMethod).left().padBottom(pad).row();

        content.add(orientation).left().padRight(pad).padBottom(pad);
        content.add(orientationMethod).left().padBottom(pad).row();

        content.add(time).left().padRight(pad).padBottom(pad * 3f);
        content.add(timeMethod).left().padBottom(pad * 3f).row();

    }

    @Override
    protected void accept() {
        GlobalConf.frame.CAMERA_REC_TARGET_FPS = Integer.parseInt(camrecFps.getText());
        GlobalConf.frame.KF_PATH_TYPE_POSITION = CameraKeyframeManager.PathType.values()[posMethod.getSelectedIndex()];
        GlobalConf.frame.KF_PATH_TYPE_ORIENTATION = CameraKeyframeManager.PathType.values()[orientationMethod.getSelectedIndex()];
    }

    @Override
    protected void cancel() {

    }
}