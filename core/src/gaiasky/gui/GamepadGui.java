package gaiasky.gui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.gui.beans.CameraComboBoxBean;
import gaiasky.input.GuiGamepadListener;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.camera.CameraManager;
import gaiasky.scene.camera.CameraManager.CameraMode;
import gaiasky.scene.view.FilterView;
import gaiasky.scene.view.FocusView;
import gaiasky.util.*;
import gaiasky.util.Settings.ControlsSettings.GamepadSettings;
import gaiasky.util.gdx.contrib.postprocess.effects.CubemapProjections.CubemapProjection;
import gaiasky.util.i18n.I18n;
import gaiasky.util.scene2d.*;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.*;

/**
 * GUI that is operated with a game controller and optimized for that purpose.
 */
public class GamepadGui extends AbstractGui {
    private static final Logger.Log logger = Logger.getLogger(GamepadGui.class.getSimpleName());

    private final Table content, menu;
    // Contains a matrix (column major) of actors for each tab
    private final List<Actor[][]> model;
    private final List<OwnTextButton> tabButtons;
    private final List<ScrollPane> tabContents;
    private final EventManager em;
    private final float pad5;
    private final float pad10;
    private final float pad20;
    private final float pad30;
    private final float pad40;
    private final FocusView view;
    private final FilterView filterView;
    boolean hackProgrammaticChangeEvents = true;
    private Table searchT, camT, timeT, graphicsT, typesT, controlsT, sysT;
    private Cell<?> contentCell, infoCell;
    private OwnTextButton searchButton, cameraButton, timeButton, graphicsButton, typesButton, controlsButton, systemButton;
    private OwnTextIconButton button3d, buttonDome, buttonCubemap, buttonOrthosphere;
    private OwnCheckBox cinematic;
    private OwnSelectBox<CameraComboBoxBean> cameraMode;
    private OwnTextButton timeStartStop, timeUp, timeDown, timeReset, quit, motionBlurButton, flareButton, starGlowButton, invertYButton, invertXButton;
    private OwnSliderPlus fovSlider, camSpeedSlider, camRotSlider, camTurnSlider, bloomSlider, unsharpMaskSlider, starBrightness, magnitudeMultiplier, starGlowFactor, pointSize, starBaseLevel;
    private OwnTextField searchField;
    private OwnLabel infoMessage;
    private Actor[][] currentModel;
    private Scene scene;
    private GamepadGuiListener gamepadListener;
    private Set<ControllerListener> backupGamepadListeners;
    private String currentInputText = "";
    private int selectedTab = 0;
    private int fi = 0, fj = 0;

    public GamepadGui(final Skin skin, final Graphics graphics, final Float unitsPerPixel) {
        super(graphics, unitsPerPixel);
        this.skin = skin;
        this.em = EventManager.instance;
        model = new ArrayList<>();
        content = new Table(skin);
        menu = new Table(skin);
        tabButtons = new ArrayList<>();
        tabContents = new ArrayList<>();
        pad5 = 8f;
        pad10 = 16f;
        pad20 = 32f;
        pad30 = 48;
        pad40 = 98;
        view = new FocusView();
        filterView = new FilterView();
    }

    @Override
    public void initialize(AssetManager assetManager, SpriteBatch sb) {
        // User interface
        ScreenViewport vp = new ScreenViewport();
        vp.setUnitsPerPixel(unitsPerPixel);
        stage = new Stage(vp, sb);

        gamepadListener = new GamepadGuiListener(this, Settings.settings.controls.gamepad.mappingsFile);

        // Comment to hide this whole dialog and functionality
        EventManager.instance.subscribe(this, Event.SHOW_CONTROLLER_GUI_ACTION, Event.TIME_STATE_CMD, Event.SCENE_LOADED, Event.CAMERA_MODE_CMD);
        EventManager.instance.subscribe(this, Event.STAR_POINT_SIZE_CMD, Event.STAR_BRIGHTNESS_CMD, Event.STAR_BRIGHTNESS_POW_CMD, Event.STAR_GLOW_FACTOR_CMD, Event.STAR_BASE_LEVEL_CMD, Event.LABEL_SIZE_CMD, Event.LINE_WIDTH_CMD);
        EventManager.instance.subscribe(this, Event.CUBEMAP_CMD, Event.STEREOSCOPIC_CMD);
    }

    @Override
    protected void rebuildGui() {

        // Clean up
        content.clear();
        menu.clear();
        tabButtons.clear();
        tabContents.clear();
        model.clear();

        float w = Math.min(Gdx.graphics.getWidth(), 1450f) - 60f;
        float h = Math.min(Gdx.graphics.getHeight(), 860f) - 60f;
        // Widget width
        float ww = 400f;
        float wh = 64f;
        float sh = 96f;
        float tfw = 240f;
        // Tab width
        float tw = 224f;

        // Create contents

        // SEARCH
        Actor[][] searchModel = new Actor[11][5];
        model.add(searchModel);

        searchT = new Table(skin);
        searchT.setSize(w, h);

        searchField = new OwnTextField("", skin, "big");
        searchField.setProgrammaticChangeEvents(true);
        searchField.setSize(ww, wh);
        searchField.setMessageText("Search...");
        searchField.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                if (!searchField.getText().equals(currentInputText) && !searchField.getText().isBlank()) {
                    // Process only if text changed
                    currentInputText = searchField.getText();
                    String name = currentInputText.toLowerCase().trim();
                    if (!checkString(name, scene)) {
                        if (name.matches("[0-9]+")) {
                            // Check with 'HIP '
                            checkString("hip " + name, scene);
                        } else if (name.matches("hip [0-9]+") || name.matches("HIP [0-9]+")) {
                            // Check without 'HIP '
                            checkString(name.substring(4), scene);
                        }
                    }
                }

            }
            return false;
        });
        searchT.add(searchField).colspan(11).padBottom(pad5).row();

        infoMessage = new OwnLabel("", skin, "default-blue");
        infoCell = searchT.add();
        infoCell.colspan(10).padBottom(pad20).row();

        // First row
        addTextKey("Q", searchModel, 0, 0, false);
        addTextKey("W", searchModel, 1, 0, false);
        addTextKey("E", searchModel, 2, 0, false);
        addTextKey("R", searchModel, 3, 0, false);
        addTextKey("T", searchModel, 4, 0, false);
        addTextKey("Y", searchModel, 5, 0, false);
        addTextKey("U", searchModel, 6, 0, false);
        addTextKey("I", searchModel, 7, 0, false);
        addTextKey("O", searchModel, 8, 0, false);
        addTextKey("P", searchModel, 9, 0, false);
        addTextKey("<--", (event) -> {
            if (event instanceof ChangeEvent) {
                if (!searchField.getText().isBlank()) {
                    searchField.setText(searchField.getText().substring(0, searchField.getText().length() - 1));
                }
            }
            return false;
        }, searchModel, 10, 0, true, tfw / 1.5f, 0);
        // Second row
        searchT.add().padRight(pad5).padBottom(pad10);
        addTextKey("A", searchModel, 1, 1, false);
        addTextKey("S", searchModel, 2, 1, false);
        addTextKey("D", searchModel, 3, 1, false);
        addTextKey("F", searchModel, 4, 1, false);
        addTextKey("G", searchModel, 5, 1, false);
        addTextKey("H", searchModel, 6, 1, false);
        addTextKey("J", searchModel, 7, 1, false);
        addTextKey("K", searchModel, 8, 1, false);
        addTextKey("L", searchModel, 9, 1, false);
        addTextKey("Clear", (event) -> {
            if (event instanceof ChangeEvent) {
                if (!searchField.getText().isBlank()) {
                    searchField.setText("");
                }
            }
            return false;
        }, searchModel, 10, 1, true, tfw / 1.5f, 0);
        // Third row
        searchT.add().padRight(pad5).padBottom(pad10);
        searchT.add().padRight(pad5).padBottom(pad10);
        addTextKey("Z", searchModel, 2, 2, false);
        addTextKey("X", searchModel, 3, 2, false);
        addTextKey("C", searchModel, 4, 2, false);
        addTextKey("V", searchModel, 5, 2, false);
        addTextKey("B", searchModel, 6, 2, false);
        addTextKey("N", searchModel, 7, 2, false);
        addTextKey("M", searchModel, 8, 2, false);
        addTextKey("-", searchModel, 9, 2, true);

        // Fourth row
        searchT.add().padRight(pad5).padBottom(pad10);
        searchT.add().padRight(pad5).padBottom(pad10);
        searchT.add().padRight(pad5).padBottom(pad10);
        addTextKey("SPACE", (event) -> {
            if (event instanceof ChangeEvent) {
                searchField.setText(searchField.getText() + " ");
            }
            return false;
        }, searchModel, 5, 3, false, tfw * 2f, 6);

        tabContents.add(container(searchT, w, h));
        updatePads(searchT);

        // CAMERA
        Actor[][] cameraModel = new Actor[4][7];
        model.add(cameraModel);

        camT = new Table(skin);
        camT.setSize(w, h);
        CameraManager cm = GaiaSky.instance.getCameraManager();

        // Camera mode
        final Label modeLabel = new Label(I18n.msg("gui.camera.mode"), skin, "header-raw");
        final int cameraModes = CameraMode.values().length;
        final CameraComboBoxBean[] cameraOptions = new CameraComboBoxBean[cameraModes];
        for (int i = 0; i < cameraModes; i++) {
            cameraOptions[i] = new CameraComboBoxBean(Objects.requireNonNull(CameraMode.getMode(i)).toStringI18n(), CameraMode.getMode(i));
        }
        cameraMode = new OwnSelectBox<>(skin, "big");
        cameraModel[0][0] = cameraMode;
        cameraMode.setWidth(ww);
        cameraMode.setItems(cameraOptions);
        cameraMode.setSelectedIndex(cm.getMode().ordinal());
        cameraMode.addListener(event -> {
            if (event instanceof ChangeEvent) {
                CameraComboBoxBean selection = cameraMode.getSelected();
                CameraMode mode = selection.mode;

                EventManager.publish(Event.CAMERA_MODE_CMD, cameraMode, mode);
                return true;
            }
            return false;
        });
        camT.add(modeLabel).right().padBottom(pad20).padRight(pad20);
        camT.add(cameraMode).left().padBottom(pad20).row();

        // Cinematic
        final Label cinematicLabel = new Label(I18n.msg("gui.camera.cinematic"), skin, "header-raw");
        cinematic = new OwnCheckBox("", skin, 0f);
        cameraModel[0][1] = cinematic;
        cinematic.setChecked(Settings.settings.scene.camera.cinematic);
        cinematic.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.CAMERA_CINEMATIC_CMD, cinematic, cinematic.isChecked());
                return true;
            }
            return false;
        });
        camT.add(cinematicLabel).right().padBottom(pad20).padRight(pad20);
        camT.add(cinematic).left().padBottom(pad20).row();

        // FOV
        final Label fovLabel = new Label(I18n.msg("gui.camera.fov"), skin, "header-raw");
        fovSlider = new OwnSliderPlus("", Constants.MIN_FOV, Constants.MAX_FOV, Constants.SLIDER_STEP_SMALL, false, skin, "header-raw");
        cameraModel[0][2] = fovSlider;
        fovSlider.setValueSuffix("??");
        fovSlider.setName("field of view");
        fovSlider.setWidth(ww);
        fovSlider.setValue(Settings.settings.scene.camera.fov);
        fovSlider.setDisabled(Settings.settings.program.modeCubemap.isFixedFov());
        fovSlider.addListener(event -> {
            if (event instanceof ChangeEvent && !SlaveManager.projectionActive() && !Settings.settings.program.modeCubemap.isFixedFov()) {
                float value = fovSlider.getMappedValue();
                EventManager.publish(Event.FOV_CHANGED_CMD, fovSlider, value);
                return true;
            }
            return false;
        });
        camT.add(fovLabel).right().padBottom(pad20).padRight(pad20);
        camT.add(fovSlider).left().padBottom(pad20).row();

        // Speed
        final Label speedLabel = new Label(I18n.msg("gui.camera.speed"), skin, "header-raw");
        camSpeedSlider = new OwnSliderPlus("", Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.SLIDER_STEP, Constants.MIN_CAM_SPEED, Constants.MAX_CAM_SPEED, skin, "header-raw");
        cameraModel[0][3] = camSpeedSlider;
        camSpeedSlider.setName("camera speed");
        camSpeedSlider.setWidth(ww);
        camSpeedSlider.setMappedValue(Settings.settings.scene.camera.speed);
        camSpeedSlider.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.CAMERA_SPEED_CMD, camSpeedSlider, camSpeedSlider.getMappedValue(), false);
                return true;
            }
            return false;
        });
        camT.add(speedLabel).right().padBottom(pad20).padRight(pad20);
        camT.add(camSpeedSlider).left().padBottom(pad20).row();

        // Rot
        final Label rotationLabel = new Label(I18n.msg("gui.rotation.speed"), skin, "header-raw");
        camRotSlider = new OwnSliderPlus("", Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.SLIDER_STEP, Constants.MIN_ROT_SPEED, Constants.MAX_ROT_SPEED, skin, "header-raw");
        cameraModel[0][4] = camRotSlider;
        camRotSlider.setName("rotate speed");
        camRotSlider.setWidth(ww);
        camRotSlider.setMappedValue(Settings.settings.scene.camera.rotate);
        camRotSlider.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.ROTATION_SPEED_CMD, camRotSlider, camRotSlider.getMappedValue());
                return true;
            }
            return false;
        });
        camT.add(rotationLabel).right().padBottom(pad20).padRight(pad20);
        camT.add(camRotSlider).left().padBottom(pad20).row();

        // Turn
        final Label turnLabel = new Label(I18n.msg("gui.turn.speed"), skin, "header-raw");
        camTurnSlider = new OwnSliderPlus("", Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.SLIDER_STEP, Constants.MIN_TURN_SPEED, Constants.MAX_TURN_SPEED, skin, "header-raw");
        cameraModel[0][5] = camTurnSlider;
        camTurnSlider.setName("turn speed");
        camTurnSlider.setWidth(ww);
        camTurnSlider.setMappedValue(Settings.settings.scene.camera.turn);
        camTurnSlider.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.TURNING_SPEED_CMD, camTurnSlider, camTurnSlider.getMappedValue(), false);
                return true;
            }
            return false;
        });
        camT.add(turnLabel).right().padBottom(pad20).padRight(pad20);
        camT.add(camTurnSlider).left().padBottom(pad20).row();

        // Mode buttons
        Table modeButtons = new Table(skin);

        final Image icon3d = new Image(skin.getDrawable("3d-icon"));
        button3d = new OwnTextIconButton("", icon3d, skin, "toggle");
        cameraModel[0][6] = button3d;
        button3d.setChecked(Settings.settings.program.modeStereo.active);
        final String hk3d = KeyBindings.instance.getStringKeys("action.toggle/element.stereomode");
        button3d.addListener(new OwnTextHotkeyTooltip(TextUtils.capitalise(I18n.msg("element.stereomode")), hk3d, skin));
        button3d.setName("3d");
        button3d.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (button3d.isChecked()) {
                    buttonCubemap.setProgrammaticChangeEvents(true);
                    buttonCubemap.setChecked(false);
                    buttonDome.setProgrammaticChangeEvents(true);
                    buttonDome.setChecked(false);
                    buttonOrthosphere.setProgrammaticChangeEvents(true);
                    buttonOrthosphere.setChecked(false);
                }
                // Enable/disable
                EventManager.publish(Event.STEREOSCOPIC_CMD, button3d, button3d.isChecked());
                return true;
            }
            return false;
        });
        modeButtons.add(button3d).padRight(pad20);

        final Image iconDome = new Image(skin.getDrawable("dome-icon"));
        buttonDome = new OwnTextIconButton("", iconDome, skin, "toggle");
        cameraModel[1][6] = buttonDome;
        buttonDome.setChecked(Settings.settings.program.modeCubemap.active && Settings.settings.program.modeCubemap.isPlanetariumOn());
        final String hkDome = KeyBindings.instance.getStringKeys("action.toggle/element.planetarium");
        buttonDome.addListener(new OwnTextHotkeyTooltip(TextUtils.capitalise(I18n.msg("element.planetarium")), hkDome, skin));
        buttonDome.setName("dome");
        buttonDome.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (buttonDome.isChecked()) {
                    buttonCubemap.setProgrammaticChangeEvents(true);
                    buttonCubemap.setChecked(false);
                    button3d.setProgrammaticChangeEvents(true);
                    button3d.setChecked(false);
                    buttonOrthosphere.setProgrammaticChangeEvents(true);
                    buttonOrthosphere.setChecked(false);
                }
                // Enable/disable
                EventManager.publish(Event.CUBEMAP_CMD, buttonDome, buttonDome.isChecked(), CubemapProjection.AZIMUTHAL_EQUIDISTANT);
                fovSlider.setDisabled(buttonDome.isChecked());
                return true;
            }
            return false;
        });
        modeButtons.add(buttonDome).padRight(pad20);

        final Image iconCubemap = new Image(skin.getDrawable("cubemap-icon"));
        buttonCubemap = new OwnTextIconButton("", iconCubemap, skin, "toggle");
        cameraModel[2][6] = buttonCubemap;
        buttonCubemap.setProgrammaticChangeEvents(false);
        buttonCubemap.setChecked(Settings.settings.program.modeCubemap.active && Settings.settings.program.modeCubemap.isPanoramaOn());
        final String hkCubemap = KeyBindings.instance.getStringKeys("action.toggle/element.360");
        buttonCubemap.addListener(new OwnTextHotkeyTooltip(TextUtils.capitalise(I18n.msg("element.360")), hkCubemap, skin));
        buttonCubemap.setName("cubemap");
        buttonCubemap.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (buttonCubemap.isChecked()) {
                    buttonDome.setProgrammaticChangeEvents(true);
                    buttonDome.setChecked(false);
                    button3d.setProgrammaticChangeEvents(true);
                    button3d.setChecked(false);
                    buttonOrthosphere.setProgrammaticChangeEvents(true);
                    buttonOrthosphere.setChecked(false);
                }
                // Enable/disable
                EventManager.publish(Event.CUBEMAP_CMD, buttonCubemap, buttonCubemap.isChecked(), CubemapProjection.EQUIRECTANGULAR);
                fovSlider.setDisabled(buttonCubemap.isChecked());
                return true;
            }
            return false;
        });
        modeButtons.add(buttonCubemap).padRight(pad20);

        final Image iconOrthosphere = new Image(skin.getDrawable("orthosphere-icon"));
        buttonOrthosphere = new OwnTextIconButton("", iconOrthosphere, skin, "toggle");
        cameraModel[3][6] = buttonOrthosphere;
        buttonOrthosphere.setProgrammaticChangeEvents(false);
        buttonOrthosphere.setChecked(Settings.settings.program.modeCubemap.active && Settings.settings.program.modeCubemap.isOrthosphereOn());
        final String hkOrthosphere = KeyBindings.instance.getStringKeys("action.toggle/element.orthosphere");
        buttonOrthosphere.addListener(new OwnTextHotkeyTooltip(TextUtils.capitalise(I18n.msg("element.orthosphere")), hkOrthosphere, skin));
        buttonOrthosphere.setName("orthosphere");
        buttonOrthosphere.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (buttonOrthosphere.isChecked()) {
                    buttonCubemap.setProgrammaticChangeEvents(true);
                    buttonCubemap.setChecked(false);
                    buttonDome.setProgrammaticChangeEvents(true);
                    buttonDome.setChecked(false);
                    button3d.setProgrammaticChangeEvents(true);
                    button3d.setChecked(false);
                }
                // Enable/disable
                EventManager.publish(Event.CUBEMAP_CMD, buttonOrthosphere, buttonOrthosphere.isChecked(), CubemapProjection.ORTHOSPHERE);
                fovSlider.setDisabled(buttonOrthosphere.isChecked());
                return true;
            }
            return false;
        });
        modeButtons.add(buttonOrthosphere).padRight(pad20);

        camT.add(modeButtons).colspan(2).center().padTop(pad40);

        tabContents.add(container(camT, w, h));
        updatePads(camT);

        // TIME
        Actor[][] timeModel = new Actor[3][2];
        model.add(timeModel);

        timeT = new Table(skin);

        boolean timeOn = Settings.settings.runtime.timeOn;
        timeStartStop = new OwnTextButton(I18n.msg(timeOn ? "gui.time.pause" : "gui.time.start"), skin, "toggle-big");
        timeModel[1][0] = timeStartStop;
        timeStartStop.setWidth(ww);
        timeStartStop.setChecked(timeOn);
        timeStartStop.addListener(event -> {
            if (event instanceof ChangeEvent) {
                em.post(Event.TIME_STATE_CMD, timeStartStop, timeStartStop.isChecked());
                timeStartStop.setText(I18n.msg(timeStartStop.isChecked() ? "gui.time.pause" : "gui.time.start"));
                return true;
            }
            return false;
        });
        timeUp = new OwnTextIconButton(I18n.msg("gui.time.speedup"), Align.right, skin, "fwd");
        timeModel[2][0] = timeUp;
        timeUp.setWidth(ww);
        timeUp.addListener(event -> {
            if (event instanceof ChangeEvent) {
                em.post(Event.TIME_WARP_INCREASE_CMD, timeUp);
                return true;
            }
            return false;
        });
        timeDown = new OwnTextIconButton(I18n.msg("gui.time.slowdown"), skin, "bwd");
        timeModel[0][0] = timeDown;
        timeDown.setWidth(ww);
        timeDown.addListener(event -> {
            if (event instanceof ChangeEvent) {
                em.post(Event.TIME_WARP_DECREASE_CMD, timeDown);
                return true;
            }
            return false;
        });
        timeReset = new OwnTextIconButton(I18n.msg("action.resettime"), Align.center, skin, "reload");
        timeModel[1][1] = timeReset;
        timeReset.setWidth(ww);
        timeReset.addListener(event -> {
            if (event instanceof ChangeEvent) {
                em.post(Event.TIME_CHANGE_CMD, timeReset, Instant.now());
                return true;
            }
            return false;
        });

        timeT.add(timeDown).padBottom(pad30).padRight(pad10);
        timeT.add(timeStartStop).padBottom(pad30).padRight(pad10);
        timeT.add(timeUp).padBottom(pad30).row();
        timeT.add(timeReset).padRight(pad10).padLeft(pad10).colspan(3).padBottom(pad10).row();
        tabContents.add(container(timeT, w, h));
        updatePads(timeT);

        // TYPES
        int visTableCols = 6;
        Actor[][] typesModel = new Actor[visTableCols][7];
        model.add(typesModel);

        typesT = new Table(skin);

        float buttonPadHor = 9.6f;
        float buttonPadVert = 4f;
        ComponentType[] visibilityEntities = ComponentType.values();
        boolean[] visible = new boolean[visibilityEntities.length];
        for (int i = 0; i < visibilityEntities.length; i++)
            visible[i] = GaiaSky.instance.sceneRenderer.visible.get(visibilityEntities[i].ordinal());

        int di = 0, dj = 0;
        for (int i = 0; i < visibilityEntities.length; i++) {
            final ComponentType ct = visibilityEntities[i];
            final String name = ct.getName();
            if (name != null) {
                Button button;
                if (ct.style != null) {
                    Image icon = new Image(skin.getDrawable(ct.style));
                    button = new OwnTextIconButton("", icon, skin, "toggle");
                } else {
                    button = new OwnTextButton(name, skin, "toggle");
                }
                // Name is the key
                button.setName(ct.key);
                typesModel[di][dj] = button;

                button.setChecked(visible[i]);
                button.addListener(event -> {
                    if (event instanceof ChangeEvent) {
                        EventManager.publish(Event.TOGGLE_VISIBILITY_CMD, button, ct.key, button.isChecked());
                        return true;
                    }
                    return false;
                });
                Cell<?> c = typesT.add(button).padBottom(buttonPadVert).left();

                if ((i + 1) % visTableCols == 0) {
                    typesT.row();
                    di = 0;
                    dj++;
                } else {
                    c.padRight(buttonPadHor);
                    di++;

                }
            }
        }

        typesT.setSize(w, h);
        tabContents.add(container(typesT, w, h));
        updatePads(typesT);

        // CONTROLS
        Actor[][] controlsModel = new Actor[1][3];
        model.add(controlsModel);

        controlsT = new Table(skin);

        // Controllers list
        var controllers = Controllers.getControllers();
        Controller controller = null;
        for (var c : controllers) {
            if (!Settings.settings.controls.gamepad.isControllerBlacklisted(c.getName())) {
                // Found it!
                controller = c;
                break;
            }
        }
        if (controller == null) {
            // Error!
            OwnLabel noControllers = new OwnLabel(I18n.msg("gui.controller.nocontrollers"), skin, "header-blue");
            controlsT.add(noControllers).padBottom(pad10).row();
            controlsModel[0][0] = new OwnTextButton("", skin, "toggle-big");
        } else {
            final var controllerName = controller.getName();
            Table controllerTable = new Table(skin);

            OwnLabel detectedController = new OwnLabel(I18n.msg("gui.controller.detected"), skin, "header-raw");
            OwnLabel controllerNameLabel = new OwnLabel(controllerName, skin, "header-blue");

            OwnTextButton configureControllerButton = new OwnTextButton(I18n.msg("gui.controller.configure"), skin, "big");
            configureControllerButton.setWidth(ww);
            configureControllerButton.setHeight(wh);
            configureControllerButton.addListener(event -> {
                if (event instanceof ChangeEvent) {
                    // Get currently selected mappings
                    GamepadMappings mappings = new GamepadMappings(controllerName, Path.of(Settings.settings.controls.gamepad.mappingsFile));
                    GamepadConfigWindow ccw = new GamepadConfigWindow(controllerName, mappings, stage, skin);
                    ccw.setAcceptRunnable(() -> {
                        if (ccw.savedFile != null) {
                            // File was saved, reload, select
                            EventManager.publish(Event.RELOAD_CONTROLLER_MAPPINGS, this, ccw.savedFile.toAbsolutePath().toString());
                        }
                    });
                    ccw.show(stage);
                    return true;
                }
                return false;
            });

            controllerTable.add(detectedController).padBottom(pad10).padRight(pad20);
            controllerTable.add(controllerNameLabel).padBottom(pad10).row();
            controllerTable.add(configureControllerButton).center().colspan(2);

            controlsT.add(controllerTable).padBottom(pad20 * 2).row();

            controlsModel[0][0] = configureControllerButton;
        }

        // Invert X
        invertXButton = new OwnTextButton(I18n.msg("gui.controller.axis.invert", "X"), skin, "toggle-big");
        controlsModel[0][1] = invertXButton;
        invertXButton.setWidth(ww);
        invertXButton.setChecked(Settings.settings.controls.gamepad.invertX);
        invertXButton.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.INVERT_X_CMD, invertXButton, invertXButton.isChecked());
                return true;
            }
            return false;
        });
        controlsT.add(invertXButton).padBottom(pad10).row();

        // Invert Y
        invertYButton = new OwnTextButton(I18n.msg("gui.controller.axis.invert", "Y"), skin, "toggle-big");
        controlsModel[0][2] = invertYButton;
        invertYButton.setWidth(ww);
        invertYButton.setChecked(Settings.settings.controls.gamepad.invertY);
        invertYButton.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.INVERT_Y_CMD, invertYButton, invertYButton.isChecked());
                return true;
            }
            return false;
        });
        controlsT.add(invertYButton);

        controlsT.setSize(w, h);
        tabContents.add(container(controlsT, w, h));
        updatePads(controlsT);

        // GRAPHICS
        Actor[][] graphicsModel = new Actor[2][7];
        model.add(graphicsModel);

        graphicsT = new Table(skin);

        // Star brightness
        starBrightness = new OwnSliderPlus(I18n.msg("gui.star.brightness"), Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.SLIDER_STEP_TINY, Constants.MIN_STAR_BRIGHTNESS, Constants.MAX_STAR_BRIGHTNESS, skin, "header-raw");
        graphicsModel[0][0] = starBrightness;
        starBrightness.setWidth(ww);
        starBrightness.setHeight(sh);
        starBrightness.setMappedValue(Settings.settings.scene.star.brightness);
        starBrightness.addListener(event -> {
            if (event instanceof ChangeEvent && hackProgrammaticChangeEvents) {
                EventManager.publish(Event.STAR_BRIGHTNESS_CMD, starBrightness, starBrightness.getMappedValue());
                return true;
            }
            return false;
        });

        // Magnitude multiplier
        magnitudeMultiplier = new OwnSliderPlus(I18n.msg("gui.star.brightness.pow"), Constants.MIN_STAR_BRIGHTNESS_POW, Constants.MAX_STAR_BRIGHTNESS_POW, Constants.SLIDER_STEP_TINY, false, skin, "header-raw");
        graphicsModel[0][1] = magnitudeMultiplier;
        magnitudeMultiplier.addListener(new OwnTextTooltip(I18n.msg("gui.star.brightness.pow.info"), skin));
        magnitudeMultiplier.setWidth(ww);
        magnitudeMultiplier.setHeight(sh);
        magnitudeMultiplier.setMappedValue(Settings.settings.scene.star.power);
        magnitudeMultiplier.addListener(event -> {
            if (event instanceof ChangeEvent && hackProgrammaticChangeEvents) {
                EventManager.publish(Event.STAR_BRIGHTNESS_POW_CMD, magnitudeMultiplier, magnitudeMultiplier.getValue());
                return true;
            }
            return false;
        });

        // Star glow factor
        starGlowFactor = new OwnSliderPlus(I18n.msg("gui.star.glowfactor"), Constants.MIN_STAR_GLOW_FACTOR, Constants.MAX_STAR_GLOW_FACTOR, Constants.SLIDER_STEP_TINY * 0.1f, false, skin, "header-raw");
        graphicsModel[0][2] = starGlowFactor;
        starGlowFactor.addListener(new OwnTextTooltip(I18n.msg("gui.star.glowfactor.info"), skin));
        starGlowFactor.setWidth(ww);
        starGlowFactor.setHeight(sh);
        starGlowFactor.setMappedValue(Settings.settings.scene.star.glowFactor);
        starGlowFactor.addListener(event -> {
            if (event instanceof ChangeEvent && hackProgrammaticChangeEvents) {
                EventManager.publish(Event.STAR_GLOW_FACTOR_CMD, starGlowFactor, starGlowFactor.getValue());
                return true;
            }
            return false;
        });

        // Point size
        pointSize = new OwnSliderPlus(I18n.msg("gui.star.size"), Constants.MIN_STAR_POINT_SIZE, Constants.MAX_STAR_POINT_SIZE, Constants.SLIDER_STEP_TINY, false, skin, "header-raw");
        graphicsModel[0][3] = pointSize;
        pointSize.setWidth(ww);
        pointSize.setHeight(sh);
        pointSize.addListener(new OwnTextTooltip(I18n.msg("gui.star.size.info"), skin));
        pointSize.setMappedValue(Settings.settings.scene.star.pointSize);
        pointSize.addListener(event -> {
            if (event instanceof ChangeEvent && hackProgrammaticChangeEvents) {
                EventManager.publish(Event.STAR_POINT_SIZE_CMD, pointSize, pointSize.getMappedValue());
                return true;
            }
            return false;
        });

        // Base star level
        starBaseLevel = new OwnSliderPlus(I18n.msg("gui.star.opacity"), Constants.MIN_STAR_MIN_OPACITY, Constants.MAX_STAR_MIN_OPACITY, Constants.SLIDER_STEP_TINY, false, skin, "header-raw");
        graphicsModel[0][4] = starBaseLevel;
        starBaseLevel.addListener(new OwnTextTooltip(I18n.msg("gui.star.opacity"), skin));
        starBaseLevel.setWidth(ww);
        starBaseLevel.setHeight(sh);
        starBaseLevel.setMappedValue(Settings.settings.scene.star.opacity[0]);
        starBaseLevel.addListener(event -> {
            if (event instanceof ChangeEvent && hackProgrammaticChangeEvents) {
                EventManager.publish(Event.STAR_BASE_LEVEL_CMD, starBaseLevel, starBaseLevel.getMappedValue());
                return true;
            }
            return false;
        });

        // Bloom
        bloomSlider = new OwnSliderPlus(I18n.msg("gui.bloom"), Constants.MIN_BLOOM, Constants.MAX_BLOOM, Constants.SLIDER_STEP_TINY, false, skin, "header-raw");
        graphicsModel[0][5] = bloomSlider;
        bloomSlider.setWidth(ww);
        bloomSlider.setHeight(sh);
        bloomSlider.setValue(Settings.settings.postprocess.bloom.intensity);
        bloomSlider.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.BLOOM_CMD, bloomSlider, bloomSlider.getValue());
                return true;
            }
            return false;
        });

        // Unsharp mask
        unsharpMaskSlider = new OwnSliderPlus(I18n.msg("gui.unsharpmask"), Constants.MIN_UNSHARP_MASK_FACTOR, Constants.MAX_UNSHARP_MASK_FACTOR, Constants.SLIDER_STEP_TINY, false, skin, "header-raw");
        graphicsModel[0][6] = unsharpMaskSlider;
        unsharpMaskSlider.setWidth(ww);
        unsharpMaskSlider.setHeight(sh);
        unsharpMaskSlider.setValue(Settings.settings.postprocess.unsharpMask.factor);
        unsharpMaskSlider.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.UNSHARP_MASK_CMD, unsharpMaskSlider, unsharpMaskSlider.getValue());
                return true;
            }
            return false;
        });

        // Lens flare
        flareButton = new OwnTextButton(I18n.msg("gui.lensflare"), skin, "toggle-big");
        graphicsModel[1][0] = flareButton;
        flareButton.setWidth(ww);
        flareButton.setChecked(Settings.settings.postprocess.lensFlare.active);
        flareButton.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.LENS_FLARE_CMD, flareButton, flareButton.isChecked());
                return true;
            }
            return false;
        });

        // Star glow
        starGlowButton = new OwnTextButton(I18n.msg("gui.lightscattering"), skin, "toggle-big");
        graphicsModel[1][1] = starGlowButton;
        starGlowButton.setWidth(ww);
        starGlowButton.setChecked(Settings.settings.postprocess.lightGlow.active);
        starGlowButton.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.LIGHT_GLOW_CMD, starGlowButton, starGlowButton.isChecked());
                return true;
            }
            return false;
        });

        // Motion blur
        motionBlurButton = new OwnTextButton(I18n.msg("gui.motionblur"), skin, "toggle-big");
        graphicsModel[1][2] = motionBlurButton;
        motionBlurButton.setWidth(ww);
        motionBlurButton.setChecked(Settings.settings.postprocess.motionBlur.active);
        motionBlurButton.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.MOTION_BLUR_CMD, motionBlurButton, motionBlurButton.isChecked());
                return true;
            }
            return false;
        });

        /* Reset defaults */
        OwnTextIconButton resetDefaults = new OwnTextIconButton(I18n.msg("gui.resetdefaults"), skin, "reset");
        graphicsModel[1][3] = resetDefaults;
        resetDefaults.align(Align.center);
        resetDefaults.setWidth(ww);
        resetDefaults.addListener(new OwnTextTooltip(I18n.msg("gui.resetdefaults.tooltip"), skin));
        resetDefaults.addListener(event -> {
            if (event instanceof ChangeEvent) {
                // Read defaults from internal settings file
                try {
                    Path confFolder = Settings.assetsPath("conf");
                    Path internalFolderConfFile = confFolder.resolve(SettingsManager.getConfigFileName(Settings.settings.runtime.openVr));
                    Yaml yaml = new Yaml();
                    Map<Object, Object> conf = yaml.load(Files.newInputStream(internalFolderConfFile));

                    float br = ((Double) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("star")).get("brightness")).floatValue();
                    float pow = ((Double) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("star")).get("power")).floatValue();
                    float glo = ((Double) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("star")).get("glowFactor")).floatValue();
                    float ss = ((Double) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("star")).get("pointSize")).floatValue();
                    float pam = (((java.util.List<Double>) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("star")).get("opacity")).get(0)).floatValue();
                    float amb = ((Double) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("renderer")).get("ambient")).floatValue();
                    float ls = ((Double) ((Map<String, Object>) ((Map<String, Object>) conf.get("scene")).get("label")).get("size")).floatValue();
                    float lw = ((Double) ((Map<String, Object>) conf.get("scene")).get("lineWidth")).floatValue();
                    float em = ((Double) ((Map<String, Object>) ((Map<String, Object>) ((Map<Object, Object>) conf.get("scene")).get("renderer")).get("elevation")).get("multiplier")).floatValue();

                    // Events
                    EventManager m = EventManager.instance;
                    m.post(Event.STAR_BRIGHTNESS_CMD, resetDefaults, br);
                    m.post(Event.STAR_BRIGHTNESS_POW_CMD, resetDefaults, pow);
                    m.post(Event.STAR_GLOW_FACTOR_CMD, resetDefaults, glo);
                    m.post(Event.STAR_POINT_SIZE_CMD, resetDefaults, ss);
                    m.post(Event.STAR_BASE_LEVEL_CMD, resetDefaults, pam);
                    m.post(Event.AMBIENT_LIGHT_CMD, resetDefaults, amb);
                    m.post(Event.LABEL_SIZE_CMD, resetDefaults, ls);
                    m.post(Event.LINE_WIDTH_CMD, resetDefaults, lw);
                    m.post(Event.ELEVATION_MULTIPLIER_CMD, resetDefaults, em);

                } catch (IOException e) {
                    logger.error(e, "Error loading default configuration file");
                }

                return true;
            }
            return false;
        });

        // Add to table
        graphicsT.add(starBrightness).padBottom(pad10).padRight(pad40);
        graphicsT.add(flareButton).padBottom(pad10).row();
        graphicsT.add(magnitudeMultiplier).padBottom(pad10).padRight(pad40);
        graphicsT.add(starGlowButton).padBottom(pad10).row();
        graphicsT.add(starGlowFactor).padBottom(pad10).padRight(pad40);
        graphicsT.add(motionBlurButton).padBottom(pad10).row();
        graphicsT.add(pointSize).padBottom(pad10).padRight(pad40);
        graphicsT.add(resetDefaults).padBottom(pad10).row();
        graphicsT.add(starBaseLevel).padBottom(pad10).padRight(pad40);
        graphicsT.add().row();
        graphicsT.add(bloomSlider).padBottom(pad10).padRight(pad40);
        graphicsT.add().row();
        graphicsT.add(unsharpMaskSlider).padBottom(pad10).padRight(pad40);
        graphicsT.add().row();

        tabContents.add(container(graphicsT, w, h));
        updatePads(graphicsT);

        // SYSTEM
        Actor[][] systemModel = new Actor[1][1];
        model.add(systemModel);

        sysT = new Table(skin);

        quit = new OwnTextIconButton(I18n.msg("gui.quit.title"), Align.center, skin, "quit");
        systemModel[0][0] = quit;
        quit.setWidth(ww);
        quit.addListener(event -> {
            if (event instanceof ChangeEvent) {
                GaiaSky.postRunnable(() -> Gdx.app.exit());
                return true;
            }
            return false;
        });
        sysT.add(quit);

        tabContents.add(container(sysT, w, h));
        updatePads(sysT);

        // Create tab buttons
        searchButton = new OwnTextButton(I18n.msg("gui.search"), skin, "toggle-big");
        tabButtons.add(searchButton);
        searchButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(searchButton);
                updateTabs();
            }
            return false;
        });

        cameraButton = new OwnTextButton(I18n.msg("gui.camera"), skin, "toggle-big");
        tabButtons.add(cameraButton);
        cameraButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(cameraButton);
                updateTabs();
            }
            return false;
        });

        timeButton = new OwnTextButton(I18n.msg("gui.time"), skin, "toggle-big");
        tabButtons.add(timeButton);
        timeButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(timeButton);
                updateTabs();
            }
            return false;
        });

        typesButton = new OwnTextButton(I18n.msg("gui.types"), skin, "toggle-big");
        tabButtons.add(typesButton);
        typesButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(typesButton);
                updateTabs();
            }
            return false;
        });

        controlsButton = new OwnTextButton(I18n.msg("gui.controls"), skin, "toggle-big");
        tabButtons.add(controlsButton);
        controlsButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(controlsButton);
                updateTabs();
            }
            return false;
        });

        graphicsButton = new OwnTextButton(I18n.msg("gui.graphics"), skin, "toggle-big");
        tabButtons.add(graphicsButton);
        graphicsButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(graphicsButton);
                updateTabs();
            }
            return false;
        });

        systemButton = new OwnTextButton(I18n.msg("gui.system"), skin, "toggle-big");
        tabButtons.add(systemButton);
        systemButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                selectedTab = tabButtons.indexOf(systemButton);
                updateTabs();
            }
            return false;
        });

        // Tab buttons styling.
        for (OwnTextButton b : tabButtons) {
            b.pad(pad10);
            b.setMinWidth(tw);
        }

        // Left and Right indicators.
        OwnTextButton lb, rb;
        rb = new OwnTextIconButton("RB", Align.left, skin, "caret-down");
        rb.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                tabRight();
            }
            return false;
        });
        rb.pad(pad10);
        lb = new OwnTextIconButton("LB", Align.left, skin, "caret-up");
        lb.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                tabLeft();
            }
            return false;
        });
        lb.pad(pad10);
        menu.add(lb).center().padBottom(pad30).row();
        menu.add(searchButton).left().row();
        menu.add(cameraButton).left().row();
        menu.add(timeButton).left().row();
        menu.add(typesButton).left().row();
        menu.add(controlsButton).left().row();
        menu.add(graphicsButton).left().row();
        menu.add(systemButton).left().padBottom(pad30).row();
        menu.add(rb).center();

        Table padTable = new Table(skin);
        padTable.pad(pad30);
        padTable.setBackground("table-border");
        menu.pack();
        padTable.add(menu).left();
        contentCell = padTable.add().center();

        content.add(padTable);

        content.setFillParent(true);
        content.center();
        content.pack();

        updateTabs();
        updateFocused(true);

        if (scene == null)
            scene = GaiaSky.instance.scene;
    }

    private void addTextKey(String text, Actor[][] m, int i, int j, boolean nl) {
        addTextKey(text, (event) -> {
            if (event instanceof ChangeEvent) {
                searchField.setText(searchField.getText() + text.toLowerCase());
            }
            return false;
        }, m, i, j, nl, -1, 1);
    }

    private void addTextKey(String text, EventListener el, Actor[][] m, int i, int j, boolean nl, float width, int colspan) {
        OwnTextButton key = new OwnTextButton(text, skin, "big");
        if (width > 0)
            key.setWidth(width);
        key.addListener(el);
        m[i][j] = key;
        var c = searchT.add(key).padRight(pad5).padBottom(pad10);
        if (nl)
            c.row();
        if (colspan > 1)
            c.colspan(colspan);
    }

    public boolean checkString(String text, Scene scene) {
        try {
            if (scene.index().containsEntity(text)) {
                Entity node = scene.index().getEntity(text);
                if (Mapper.focus.has(node)) {
                    view.setEntity(node);
                    view.getFocus(text);
                    filterView.setEntity(node);
                    boolean timeOverflow = view.isCoordinatesTimeOverflow();
                    boolean canSelect = !view.isSet() || view.getSet().canSelect(filterView);
                    boolean ctOn = GaiaSky.instance.isOn(view.getCt());
                    if (!timeOverflow && canSelect && ctOn) {
                        GaiaSky.postRunnable(() -> {
                            EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraMode.FOCUS_MODE, true);
                            EventManager.publish(Event.FOCUS_CHANGE_CMD, this, node, true);
                        });
                        info(null);
                    } else if (timeOverflow) {
                        info(I18n.msg("gui.objects.search.timerange", text));
                    } else if (!canSelect) {
                        info(I18n.msg("gui.objects.search.filter", text));
                    } else {
                        info(I18n.msg("gui.objects.search.invisible", text, view.getCt().toString()));
                    }
                    return true;
                }
            } else {
                info(null);
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return false;
    }

    private void info(String info) {
        if (info == null) {
            infoMessage.setText("");
            info(false);
        } else {
            infoMessage.setText(info);
            info(true);
        }
    }

    private void info(boolean visible) {
        if (visible) {
            infoCell.setActor(infoMessage);
        } else {
            infoCell.setActor(null);
        }
    }

    @Override
    public void doneLoading(AssetManager assetManager) {
        rebuildGui();
        stage.setKeyboardFocus(null);
    }

    @Override
    public void update(double dt) {
        super.update(dt);
        if (gamepadListener.isActive()) {
            gamepadListener.update();
        }
    }

    private ScrollPane container(Table t, float w, float h) {
        var c = new OwnScrollPane(t, skin, "minimalist-nobg");
        t.center();
        c.setFadeScrollBars(true);
        c.setForceScroll(false, false);
        c.setSize(w, h);
        return c;
    }

    private void updatePads(Table t) {
        var cells = t.getCells();
        for (Cell<?> c : cells) {
            if (c.getActor() instanceof Button && !(c.getActor() instanceof CheckBox)) {
                ((Button) c.getActor()).pad(pad20);
            }
        }
    }

    public void updateTabs() {
        for (OwnTextButton tb : tabButtons) {
            tb.setProgrammaticChangeEvents(false);
            tb.setChecked(false);
            tb.setProgrammaticChangeEvents(true);
        }
        tabButtons.get(selectedTab).setProgrammaticChangeEvents(false);
        tabButtons.get(selectedTab).setChecked(true);
        tabButtons.get(selectedTab).setProgrammaticChangeEvents(true);
        contentCell.setActor(null);
        currentModel = model.get(selectedTab);
        contentCell.setActor(tabContents.get(selectedTab));
        selectFirst();
        updateFocused();
    }

    /**
     * Selects the given object. If it is null, it scans the row in the given direction until
     * all elements have been scanned.
     *
     * @param i     The column
     * @param j     The row
     * @param right Whether scan right or left
     *
     * @return True if the element was selected, false otherwise
     */
    public boolean selectInRow(int i, int j, boolean right) {
        fi = i;
        fj = j;
        if (currentModel != null && currentModel.length > 0) {
            while (currentModel[fi][fj] == null) {
                // Move to next column
                fi = (fi + (right ? 1 : -1)) % currentModel.length;
                if (fi < 0) {
                    fi = currentModel.length - 1;
                }
                if (fi == i) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Selects the given object. If it is null, it scans the column in the given direction until
     * all elements have been scanned.
     *
     * @param i    The column
     * @param j    The row
     * @param down Whether scan up or down
     *
     * @return True if the element was selected, false otherwise
     */
    public boolean selectInCol(int i, int j, boolean down) {
        fi = i;
        fj = j;
        if (currentModel != null && currentModel.length > 0) {
            while (currentModel[fi][fj] == null) {
                // Try out other columns
                if (selectInRow(fi, fj, true))
                    return true;
                // Move to the next row
                fj = (fj + (down ? 1 : -1)) % currentModel[fi].length;
                if (fj == j) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void selectFirst() {
        selectInRow(0, 0, true);
    }

    public void updateFocused() {
        updateFocused(false);
    }

    public void updateFocused(boolean force) {
        if ((force || content.getParent() != null) && currentModel != null && currentModel.length != 0) {
            Actor actor = currentModel[fi][fj];
            if (GuiUtils.isInputWidget(actor)) {
                stage.setKeyboardFocus(actor);
            }
        }
    }

    public void tabLeft() {
        if (selectedTab - 1 < 0) {
            selectedTab = tabButtons.size() - 1;
        } else {
            selectedTab--;
        }
        updateTabs();
    }

    public void tabRight() {
        selectedTab = (selectedTab + 1) % tabButtons.size();
        updateTabs();
    }

    public void up() {
        if (selectInCol(fi, update(fj, -1, currentModel[fi].length), false)) {
            updateFocused();
        }
    }

    public void down() {
        if (selectInCol(fi, update(fj, 1, currentModel[fi].length), true)) {
            updateFocused();
        }
    }

    public void left() {
        if (selectInRow(update(fi, -1, currentModel.length), fj, false)) {
            updateFocused();
        }
    }

    public void right() {
        if (selectInRow(update(fi, 1, currentModel.length), fj, true)) {
            updateFocused();
        }
    }

    private Actor getFocusedActor() {
        return currentModel != null ? currentModel[fi][fj] : null;
    }

    private int update(int val, int inc, int len) {
        if (len <= 0)
            return val;
        if (inc >= 0) {
            return (val + inc) % len;
        } else {
            if (val + inc < 0) {
                return inc + len;
            } else {
                return val + inc;
            }
        }
    }

    public void back() {
        EventManager.publish(Event.SHOW_CONTROLLER_GUI_ACTION, this);
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        // Empty by default
        switch (event) {
        case SCENE_LOADED -> this.scene = (Scene) data[0];
        case SHOW_CONTROLLER_GUI_ACTION -> {
            if (content.isVisible() && content.hasParent()) {
                removeControllerGui();
            } else {
                addControllerGui();
            }
        }
        case TIME_STATE_CMD -> {
            boolean on = (Boolean) data[0];
            timeStartStop.setProgrammaticChangeEvents(false);
            timeStartStop.setChecked(on);
            timeStartStop.setText(on ? "Stop time" : "Start time");
            timeStartStop.setProgrammaticChangeEvents(true);
        }
        case CAMERA_MODE_CMD -> {
            if (source != cameraMode) {
                // Update camera mode selection
                final var mode = (CameraMode) data[0];
                var cModes = cameraMode.getItems();
                CameraComboBoxBean selected = null;
                for (var cameraModeBean : cModes) {
                    if (cameraModeBean.mode == mode) {
                        selected = cameraModeBean;
                        break;
                    }
                }
                if (selected != null) {
                    cameraMode.getSelection().setProgrammaticChangeEvents(false);
                    cameraMode.setSelected(selected);
                    cameraMode.getSelection().setProgrammaticChangeEvents(true);
                }
            }
        }
        case STAR_POINT_SIZE_CMD -> {
            if (source != pointSize) {
                hackProgrammaticChangeEvents = false;
                float newSize = (float) data[0];
                pointSize.setMappedValue(newSize);
                hackProgrammaticChangeEvents = true;
            }
        }
        case STAR_BRIGHTNESS_CMD -> {
            if (source != starBrightness) {
                Float brightness = (Float) data[0];
                hackProgrammaticChangeEvents = false;
                starBrightness.setMappedValue(brightness);
                hackProgrammaticChangeEvents = true;
            }
        }
        case STAR_BRIGHTNESS_POW_CMD -> {
            if (source != magnitudeMultiplier) {
                Float pow = (Float) data[0];
                hackProgrammaticChangeEvents = false;
                magnitudeMultiplier.setMappedValue(pow);
                hackProgrammaticChangeEvents = true;
            }
        }
        case STAR_GLOW_FACTOR_CMD -> {
            if (source != starGlowFactor) {
                Float glowFactor = (Float) data[0];
                hackProgrammaticChangeEvents = false;
                starGlowFactor.setMappedValue(glowFactor);
                hackProgrammaticChangeEvents = true;
            }
        }
        case STAR_BASE_LEVEL_CMD -> {
            if (source != starBaseLevel) {
                Float baseLevel = (Float) data[0];
                hackProgrammaticChangeEvents = false;
                starBaseLevel.setMappedValue(baseLevel);
                hackProgrammaticChangeEvents = true;
            }
        }
        case STEREOSCOPIC_CMD -> {
            if (source != button3d && !Settings.settings.runtime.openVr) {
                button3d.setProgrammaticChangeEvents(false);
                button3d.setChecked((boolean) data[0]);
                button3d.setProgrammaticChangeEvents(true);
            }
        }
        case CUBEMAP_CMD -> {
            if (!Settings.settings.runtime.openVr) {
                final CubemapProjection proj = (CubemapProjection) data[1];
                final boolean enable = (boolean) data[0];
                if (proj.isPanorama() && source != buttonCubemap) {
                    buttonCubemap.setProgrammaticChangeEvents(false);
                    buttonCubemap.setChecked(enable);
                    buttonCubemap.setProgrammaticChangeEvents(true);
                    fovSlider.setDisabled(enable);
                } else if (proj.isPlanetarium() && source != buttonDome) {
                    buttonDome.setProgrammaticChangeEvents(false);
                    buttonDome.setChecked(enable);
                    buttonDome.setProgrammaticChangeEvents(true);
                    fovSlider.setDisabled(enable);
                } else if (proj.isOrthosphere() && source != buttonOrthosphere) {
                    buttonOrthosphere.setProgrammaticChangeEvents(false);
                    buttonOrthosphere.setChecked(enable);
                    buttonOrthosphere.setProgrammaticChangeEvents(true);
                    fovSlider.setDisabled(enable);
                }
            }
        }
        default -> {
        }
        }
    }

    public boolean removeControllerGui() {
        // Hide and remove
        if (content.isVisible() && content.hasParent()) {
            searchField.setText("");
            content.setVisible(false);
            content.remove();
            content.clear();
            stage.setKeyboardFocus(null);

            removeGamepadListener();
            return true;
        }
        return false;
    }

    public void addControllerGui() {
        // Add and show
        if (!content.isVisible() || !content.hasParent()) {
            rebuildGui();
            stage.addActor(content);
            content.setVisible(true);
            updateFocused();

            addGamepadListener();

            // Close all open windows.
            EventManager.publish(Event.CLOSE_ALL_GUI_WINDOWS_CMD, this);
        }
    }

    private void addGamepadListener() {
        if (gamepadListener != null) {
            GamepadSettings gamepadSettings = Settings.settings.controls.gamepad;
            // Backup and clean
            backupGamepadListeners = gamepadSettings.getControllerListeners();
            gamepadSettings.removeAllControllerListeners();

            // Add and activate.
            gamepadSettings.addControllerListener(gamepadListener);
        }
    }

    private void removeGamepadListener() {
        if (gamepadListener != null) {
            GamepadSettings gamepadSettings = Settings.settings.controls.gamepad;
            // Remove current listener
            gamepadSettings.removeControllerListener(gamepadListener);

            // Restore backup.
            gamepadSettings.setControllerListeners(backupGamepadListeners);
            backupGamepadListeners = null;
        }
    }

    private static class GamepadGuiListener extends GuiGamepadListener {
        private final GamepadGui gui;

        public GamepadGuiListener(GamepadGui gui, String mappingsFile) {
            super(mappingsFile, gui.stage);
            this.gui = gui;
        }

        @Override
        public boolean buttonUp(Controller controller, int buttonCode) {
            super.buttonUp(controller, buttonCode);
            if (buttonCode == mappings.getButtonA()) {
                actionUp();
                return true;
            }
            return false;
        }

        @Override
        public void actionDown() {
            Actor target = stage.getKeyboardFocus();

            if (target != null) {
                if (target instanceof CheckBox) {
                    // Check or uncheck.
                    CheckBox cb = (CheckBox) target;
                    if (!cb.isDisabled()) {
                        cb.setChecked(!cb.isChecked());
                    }
                } else if (target instanceof Button) {
                    // Touch-down event for buttons.
                    final Button b = (Button) target;
                    InputEvent inputEvent = Pools.obtain(InputEvent.class);
                    inputEvent.setTarget(b);
                    inputEvent.setType(InputEvent.Type.touchDown);
                    b.fire(inputEvent);
                    Pools.free(inputEvent);
                } else {
                    // Fire change event.
                    ChangeEvent event = Pools.obtain(ChangeEvent.class);
                    event.setTarget(target);
                    target.fire(event);
                    Pools.free(event);
                }
            }
        }

        public void actionUp() {
            Actor target = stage.getKeyboardFocus();

            if (target != null) {
                if (target instanceof Button) {
                    // Touch-up event.
                    final Button b = (Button) target;
                    InputEvent inputEvent = Pools.obtain(InputEvent.class);
                    inputEvent.setTarget(b);
                    inputEvent.setType(InputEvent.Type.touchUp);
                    b.fire(inputEvent);
                    Pools.free(inputEvent);
                }
            }
        }

        @Override
        public Array<Group> getContentContainers() {
            var a = new Array<Group>(1);
            a.add(gui.content);
            return a;
        }

        @Override
        public void back() {
            gui.back();
        }

        @Override
        public void start() {
            gui.back();
        }

        @Override
        public void select() {

        }

        @Override
        public void tabLeft() {
            gui.tabLeft();
        }

        @Override
        public void tabRight() {
            gui.tabRight();
        }

        @Override
        public void moveUp() {
            gui.up();
        }

        @Override
        public void moveDown() {
            gui.down();
        }

        @Override
        public void moveLeft() {
            gui.left();
        }

        @Override
        public void moveRight() {
            gui.right();
        }

        @Override
        public void rightStickVertical(float value) {
            super.rightStickVertical(gui.getFocusedActor(), value);
        }

        @Override
        public void rightStickHorizontal(float value) {
            super.rightStickHorizontal(gui.getFocusedActor(), value);
        }
    }

}
