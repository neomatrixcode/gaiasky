/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputEvent.Type;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Method;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.render.ComponentTypes;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.scene.Scene;
import gaiasky.scene.view.FocusView;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings.ProgramSettings.UpdateSettings;
import gaiasky.util.i18n.I18n;
import gaiasky.util.scene2d.FileChooser;
import gaiasky.util.scene2d.OwnLabel;
import gaiasky.util.update.VersionCheckEvent;
import gaiasky.util.update.VersionChecker;

import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.Instant;

import static gaiasky.event.Event.*;

/**
 * Full OpenGL GUI with all the controls and whistles.
 */
public class FullGui extends AbstractGui {
    private static final Log logger = Logger.getLogger(FullGui.class);
    private final GlobalResources globalResources;
    private final CatalogManager catalogManager;
    private final FocusView view;
    protected ControlsWindow controlsWindow;
    protected MinimapWindow minimapWindow;
    protected Container<FocusInfoInterface> fi;
    protected Container<TopInfoInterface> ti;
    protected Container<NotificationsInterface> ni;
    protected FocusInfoInterface focusInterface;
    protected NotificationsInterface notificationsInterface;
    protected MessagesInterface messagesInterface;
    protected CustomInterface customInterface;
    protected RunStateInterface runStateInterface;
    protected TopInfoInterface topInfoInterface;
    protected PopupNotificationsInterface popupNotificationsInterface;
    protected MinimapInterface minimapInterface;
    protected LoadProgressInterface loadProgressInterface;
    protected LogWindow logWindow;
    protected WikiInfoWindow wikiInfoWindow;
    protected ArchiveViewWindow archiveViewWindow;
    protected DecimalFormat nf;
    protected Label pointerXCoord, pointerYCoord;
    protected float pad, pad5;
    protected Scene scene;
    private ComponentType[] visibilityEntities;
    private boolean[] visible;

    public FullGui(final Skin skin, final Graphics graphics, final Float unitsPerPixel, final GlobalResources globalResources, final CatalogManager catalogManager) {
        super(graphics, unitsPerPixel);
        this.skin = skin;
        this.globalResources = globalResources;
        this.catalogManager = catalogManager;
        this.view = new FocusView();
    }

    @Override
    public void initialize(final AssetManager assetManager, final SpriteBatch sb) {
        // User interface
        ScreenViewport vp = new ScreenViewport();
        vp.setUnitsPerPixel(unitsPerPixel);
        this.stage = new Stage(vp, sb);
        vp.update(graphics.getWidth(), graphics.getHeight(), true);
    }

    public void initialize(Stage ui) {
        this.stage = ui;
    }

    @Override
    public void doneLoading(AssetManager assetManager) {
        logger.info(I18n.msg("notif.gui.init"));

        interfaces = new Array<>();

        buildGui();

        // We must subscribe to the desired events
        EventManager.instance.subscribe(this, FOV_CHANGED_CMD, SHOW_WIKI_INFO_ACTION, UPDATE_WIKI_INFO_ACTION, SHOW_ARCHIVE_VIEW_ACTION, UPDATE_ARCHIVE_VIEW_ACTION, SHOW_PLAYCAMERA_ACTION, REMOVE_KEYBOARD_FOCUS, REMOVE_GUI_COMPONENT, ADD_GUI_COMPONENT, SHOW_LOG_ACTION, RA_DEC_UPDATED, LON_LAT_UPDATED, POPUP_MENU_FOCUS, SHOW_LAND_AT_LOCATION_ACTION, DISPLAY_POINTER_COORDS_CMD, TOGGLE_MINIMAP, SHOW_MINIMAP_ACTION, SHOW_PROCEDURAL_GEN_ACTION);
    }

    protected void buildGui() {
        pad = 16f;
        pad5 = 8f;
        // Component types name init
        for (ComponentType ct : ComponentType.values()) {
            ct.getName();
        }
        nf = new DecimalFormat("##0.##");

        // NOTIFICATIONS INTERFACE - BOTTOM LEFT
        notificationsInterface = new NotificationsInterface(skin, lock, true, true, true, true);
        notificationsInterface.pad(pad5);
        ni = new Container<>(notificationsInterface);
        ni.setFillParent(true);
        ni.bottom().left();
        ni.pad(0, pad, pad, 0);
        interfaces.add(notificationsInterface);

        // CONTROLS WINDOW
        addControlsWindow();

        // FOCUS INFORMATION - BOTTOM RIGHT
        focusInterface = new FocusInfoInterface(skin);
        fi = new Container<>(focusInterface);
        fi.setFillParent(true);
        fi.bottom().right();
        fi.pad(0, 0, pad, pad);
        interfaces.add(focusInterface);

        // MESSAGES INTERFACE - LOW CENTER
        messagesInterface = new MessagesInterface(skin, lock);
        messagesInterface.setFillParent(true);
        messagesInterface.left().bottom();
        messagesInterface.pad(0, Gdx.graphics.getWidth() * 0.2f, Gdx.graphics.getHeight() * 0.2f, 0);
        interfaces.add(messagesInterface);

        // TOP INFO - TOP CENTER
        topInfoInterface = new TopInfoInterface(skin, scene);
        topInfoInterface.top();
        topInfoInterface.pad(pad5, pad, pad5, pad);
        ti = new Container<>(topInfoInterface);
        ti.setFillParent(true);
        ti.top();
        ti.pad(pad);
        interfaces.add(topInfoInterface);

        // MINIMAP
        initializeMinimap(stage);

        // INPUT STATE
        runStateInterface = new RunStateInterface(skin, true);
        runStateInterface.setFillParent(true);
        runStateInterface.center().bottom();
        runStateInterface.pad(0, 0, pad, 0);
        interfaces.add(runStateInterface);

        // POPUP NOTIFICATIONS
        popupNotificationsInterface = new PopupNotificationsInterface(skin);
        popupNotificationsInterface.setFillParent(true);
        popupNotificationsInterface.right().top();
        interfaces.add(popupNotificationsInterface);

        // LOAD PROGRESS INTERFACE
        addLoadProgressInterface();

        // CUSTOM OBJECTS INTERFACE
        customInterface = new CustomInterface(stage, skin, lock);
        interfaces.add(customInterface);

        // MOUSE X/Y COORDINATES
        pointerXCoord = new OwnLabel("", skin, "default");
        pointerXCoord.setAlignment(Align.bottom);
        pointerXCoord.setVisible(Settings.settings.program.pointer.coordinates);
        pointerYCoord = new OwnLabel("", skin, "default");
        pointerYCoord.setAlignment(Align.right | Align.center);
        pointerYCoord.setVisible(Settings.settings.program.pointer.coordinates);

        /* ADD TO UI */
        rebuildGui();

        /* VERSION CHECK */
        if (Settings.settings.program.update.lastCheck == null || Instant.now().toEpochMilli() - Settings.settings.program.update.lastCheck.toEpochMilli() > UpdateSettings.VERSION_CHECK_INTERVAL_MS) {
            // Start version check
            VersionChecker vc = new VersionChecker(Settings.settings.program.url.versionCheck);
            vc.setListener(event -> {
                if (event instanceof VersionCheckEvent) {
                    VersionCheckEvent vce = (VersionCheckEvent) event;
                    if (!vce.isFailed()) {
                        // Check version
                        String tagVersion = vce.getTag();
                        Integer versionNumber = vce.getVersionNumber();

                        Settings.settings.program.update.lastCheck = Instant.now();

                        if (versionNumber > Settings.settings.version.versionNumber) {
                            logger.info(I18n.msg("gui.newversion.available", Settings.settings.version.version, tagVersion));
                            // There's a new version!
                            UpdatePopup newVersion = new UpdatePopup(tagVersion, stage, skin);
                            newVersion.pack();
                            float ww = newVersion.getWidth();
                            float margin = 8f;
                            newVersion.setPosition(graphics.getWidth() - ww - margin, margin);
                            stage.addActor(newVersion);
                        } else {
                            // No new version
                            logger.info(I18n.msg("gui.newversion.nonew", Settings.settings.program.update.getLastCheckedString()));
                        }

                    } else {
                        // Handle failed case
                        // Do nothing
                        logger.info(I18n.msg("gui.newversion.fail"));
                    }
                }
                return false;
            });

            // Start in 10 seconds
            Thread vct = new Thread(vc);
            Timer.Task t = new Timer.Task() {
                @Override
                public void run() {
                    logger.info(I18n.msg("gui.newversion.checking"));
                    vct.start();
                }
            };
            Timer.schedule(t, 10);
        }

    }

    public void recalculateOptionsSize() {
        controlsWindow.recalculateSize();
    }

    protected void rebuildGui() {
        if (stage != null) {
            stage.clear();
            boolean collapsed;
            if (controlsWindow != null) {
                collapsed = controlsWindow.isCollapsed();
                recalculateOptionsSize();
                if (collapsed)
                    controlsWindow.collapseInstant();
                controlsWindow.setPosition(0, graphics.getHeight() * unitsPerPixel - controlsWindow.getHeight());
                stage.addActor(controlsWindow);
            }
            if (ni != null) {
                stage.addActor(ni);
            }
            if (messagesInterface != null) {
                stage.addActor(messagesInterface);
            }
            if (fi != null) {
                stage.addActor(fi);
            }
            if (runStateInterface != null) {
                stage.addActor(runStateInterface);
            }
            if (ti != null) {
                stage.addActor(ti);
            }
            if (minimapInterface != null) {
                stage.addActor(minimapInterface);
            }
            if (loadProgressInterface != null) {
                stage.addActor(loadProgressInterface);
            }
            if (pointerXCoord != null && pointerYCoord != null) {
                stage.addActor(pointerXCoord);
                stage.addActor(pointerYCoord);
            }
            if (customInterface != null) {
                customInterface.reAddObjects();
            }
            if (popupNotificationsInterface != null) {
                stage.addActor(popupNotificationsInterface);
            }

            /* CAPTURE SCROLL FOCUS */
            stage.addListener(new EventListener() {

                @Override
                public boolean handle(com.badlogic.gdx.scenes.scene2d.Event event) {
                    if (event instanceof InputEvent) {
                        InputEvent ie = (InputEvent) event;

                        if (ie.getType() == Type.mouseMoved) {
                            Actor scrollPanelAncestor = getScrollPanelAncestor(ie.getTarget());
                            stage.setScrollFocus(scrollPanelAncestor);
                        } else if (ie.getType() == Type.touchDown) {
                            if (ie.getTarget() instanceof TextField)
                                stage.setKeyboardFocus(ie.getTarget());
                        }
                    }
                    return false;
                }

                private Actor getScrollPanelAncestor(Actor actor) {
                    if (actor == null) {
                        return null;
                    } else if (actor instanceof ScrollPane) {
                        return actor;
                    } else {
                        return getScrollPanelAncestor(actor.getParent());
                    }
                }

            });

            /* KEYBOARD FOCUS */
            stage.addListener((event) -> {
                if (event instanceof InputEvent) {
                    InputEvent ie = (InputEvent) event;
                    if (ie.getType() == Type.touchDown && !ie.isHandled()) {
                        stage.setKeyboardFocus(null);
                    }
                }
                return false;
            });
        }
    }

    /**
     * Removes the focus from this Gui and returns true if the focus was in the
     * GUI, false otherwise.
     *
     * @return true if the focus was in the GUI, false otherwise.
     */
    public boolean cancelTouchFocus() {
        if (stage.getScrollFocus() != null) {
            stage.setScrollFocus(null);
            stage.setKeyboardFocus(null);
            return true;
        }
        return false;
    }

    @Override
    public void update(double dt) {
        stage.act((float) dt);
        for (IGuiInterface i : interfaces) {
            if (i.isOn())
                i.update();
        }
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        switch (event) {
        case SHOW_PROCEDURAL_GEN_ACTION:
            FocusView planet = (FocusView) data[0];
            Actor w = findActor("procedural-window");
            // Only one instance
            if (w != null && w.hasParent()) {
                if (!w.isVisible())
                    w.setVisible(true);
            } else {
                ProceduralGenerationWindow proceduralWindow = new ProceduralGenerationWindow(planet, stage, skin);
                proceduralWindow.setName("procedural-window");
                proceduralWindow.show(stage);
            }
            break;
        case SHOW_LAND_AT_LOCATION_ACTION:
            var target = (FocusView) data[0];
            LandAtWindow landAtLocation = new LandAtWindow(target.getEntity(), stage, skin);
            landAtLocation.show(stage);
            break;
        case SHOW_PLAYCAMERA_ACTION:
            FileChooser fc = new FileChooser(I18n.msg("gui.camera.title"), skin, stage, SysUtils.getDefaultCameraDir(), FileChooser.FileChooserTarget.FILES);
            fc.setShowHidden(Settings.settings.program.fileChooser.showHidden);
            fc.setShowHiddenConsumer((showHidden) -> Settings.settings.program.fileChooser.showHidden = showHidden);
            fc.setAcceptText(I18n.msg("gui.camera.run"));
            fc.setFileFilter(pathname -> pathname.getFileName().toString().endsWith(".dat") || pathname.getFileName().toString().endsWith(".gsc"));
            fc.setAcceptedFiles("*.dat, *.gsc");
            fc.setResultListener((success, result) -> {
                if (success) {
                    if (Files.exists(result) && Files.exists(result)) {
                        EventManager.publish(PLAY_CAMERA_CMD, fc, result);
                        return true;
                    } else {
                        logger.error("Selection must be a file: " + result.toAbsolutePath());
                    }
                }
                return false;
            });
            fc.show(stage);
            break;
        case SHOW_LOG_ACTION:
            if (logWindow == null) {
                logWindow = new LogWindow(stage, skin);
            }
            logWindow.update();
            if (!logWindow.isVisible() || !logWindow.hasParent())
                logWindow.show(stage);
            break;
        case UPDATE_WIKI_INFO_ACTION:
            if (wikiInfoWindow != null && wikiInfoWindow.isVisible() && wikiInfoWindow.hasParent() && !wikiInfoWindow.isUpdating()) {
                // Update
                String searchName = (String) data[0];
                wikiInfoWindow.update(searchName);
            }
            break;
        case SHOW_WIKI_INFO_ACTION:
            String searchName = (String) data[0];
            if (wikiInfoWindow == null) {
                wikiInfoWindow = new WikiInfoWindow(stage, skin);
            }
            if (!wikiInfoWindow.isUpdating()) {
                wikiInfoWindow.update(searchName);
                if (!wikiInfoWindow.isVisible() || !wikiInfoWindow.hasParent())
                    wikiInfoWindow.show(stage);
            }
            break;
        case UPDATE_ARCHIVE_VIEW_ACTION:
            if (archiveViewWindow != null && archiveViewWindow.isVisible() && archiveViewWindow.hasParent()) {
                // Update
                FocusView starFocus = (FocusView) data[0];
                archiveViewWindow.update(starFocus);
            }
            break;
        case SHOW_ARCHIVE_VIEW_ACTION:
            FocusView starFocus = (FocusView) data[0];
            if (archiveViewWindow == null) {
                archiveViewWindow = new ArchiveViewWindow(stage, skin);
            }
            archiveViewWindow.update(starFocus);
            if (!archiveViewWindow.isVisible() || !archiveViewWindow.hasParent())
                archiveViewWindow.show(stage);
            break;
        case REMOVE_KEYBOARD_FOCUS:
            stage.setKeyboardFocus(null);
            break;
        case REMOVE_GUI_COMPONENT:
            String name = (String) data[0];
            String method = "remove" + TextUtils.capitalise(name);
            try {
                Method m = ClassReflection.getMethod(this.getClass(), method);
                m.invoke(this);
            } catch (ReflectionException e) {
                logger.error(e);
            }
            rebuildGui();
            break;
        case ADD_GUI_COMPONENT:
            name = (String) data[0];
            method = "add" + TextUtils.capitalise(name);
            try {
                Method m = ClassReflection.getMethod(this.getClass(), method);
                m.invoke(this);
            } catch (ReflectionException e) {
                logger.error(e);
            }
            rebuildGui();
            break;
        case RA_DEC_UPDATED:
            if (Settings.settings.program.pointer.coordinates) {
                Stage ui = pointerYCoord.getStage();
                float uiScale = Settings.settings.program.ui.scale;
                Double ra = (Double) data[0];
                Double dec = (Double) data[1];
                Integer x = (Integer) data[4];
                Integer y = (Integer) data[5];

                pointerXCoord.setText(I18n.msg("gui.focusinfo.pointer.ra", nf.format(ra)));
                pointerXCoord.setPosition(x / uiScale, 1.6f);
                pointerYCoord.setText(I18n.msg("gui.focusinfo.pointer.dec", nf.format(dec)));
                pointerYCoord.setPosition(ui.getWidth() + 1.6f, ui.getHeight() - y / uiScale);
            }
            break;
        case LON_LAT_UPDATED:
            if (Settings.settings.program.pointer.coordinates) {
                Stage ui = pointerYCoord.getStage();
                float uiScale = Settings.settings.program.ui.scale;
                Double lon = (Double) data[0];
                Double lat = (Double) data[1];
                Integer x = (Integer) data[2];
                Integer y = (Integer) data[3];

                pointerXCoord.setText(I18n.msg("gui.focusinfo.pointer.lon", nf.format(lon)));
                pointerXCoord.setPosition(x / uiScale, 1.6f);
                pointerYCoord.setText(I18n.msg("gui.focusinfo.pointer.lat", nf.format(lat)));
                pointerYCoord.setPosition(ui.getWidth() + 1.6f, ui.getHeight() - y / uiScale);
            }
            break;
        case DISPLAY_POINTER_COORDS_CMD:
            Boolean display = (Boolean) data[0];
            pointerXCoord.setVisible(display);
            pointerYCoord.setVisible(display);
            break;
        case POPUP_MENU_FOCUS:
            final Entity candidate = (Entity) data[0];
            int screenX = Gdx.input.getX();
            int screenY = Gdx.input.getY();

            FocusView focusView = null;
            if (candidate != null) {
                view.setEntity(candidate);
                focusView = view;
            }

            GaiaSkyContextMenu popup = new GaiaSkyContextMenu(skin, "default", screenX, screenY, focusView, catalogManager, scene);

            int h = (int) getGuiStage().getHeight();

            float px = screenX / Settings.settings.program.ui.scale;
            float py = h - screenY / Settings.settings.program.ui.scale - 32f;

            popup.showMenu(stage, px, py);

            break;
        case TOGGLE_MINIMAP:
            if (Settings.settings.program.minimap.inWindow) {
                toggleMinimapWindow(stage);
            } else {
                toggleMinimapInterface(stage);
            }
            break;
        case SHOW_MINIMAP_ACTION:
            boolean show = (Boolean) data[0];
            if (Settings.settings.program.minimap.inWindow) {
                showMinimapWindow(stage, show);
            } else {
                showMinimapInterface(stage, show);
            }
            break;
        default:
            break;
        }
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    @Override
    public void setVisibilityToggles(ComponentType[] entities, ComponentTypes visible) {
        this.visibilityEntities = entities;
        ComponentType[] values = ComponentType.values();
        this.visible = new boolean[values.length];
        for (int i = 0; i < values.length; i++)
            this.visible[i] = visible.get(values[i].ordinal());
    }

    public void addControlsWindow() {
        controlsWindow = new ControlsWindow(Settings.getSuperShortApplicationName(), skin, stage, catalogManager);
        controlsWindow.setScene(scene);
        controlsWindow.setVisibilityToggles(visibilityEntities, visible);
        controlsWindow.initialize();
        controlsWindow.left();
        controlsWindow.getTitleTable().align(Align.left);
        controlsWindow.setFillParent(false);
        controlsWindow.setMovable(true);
        controlsWindow.setResizable(false);
        controlsWindow.padRight(5);
        controlsWindow.padBottom(5);

        controlsWindow.collapseInstant();
    }

    public void initializeMinimap(Stage ui) {
        if (Settings.settings.program.minimap.active) {
            if (Settings.settings.program.minimap.inWindow) {
                showMinimapWindow(ui, true);
            } else {
                if (minimapInterface == null) {
                    minimapInterface = new MinimapInterface(skin, globalResources.getShapeShader(), globalResources.getSpriteShader());
                    minimapInterface.setFillParent(true);
                    minimapInterface.right().top();
                    minimapInterface.pad(pad, 0f, 0f, pad);
                    interfaces.add(minimapInterface);
                }
            }
        }
    }

    public void showMinimapInterface(Stage ui, boolean show) {
        if (minimapInterface == null) {
            minimapInterface = new MinimapInterface(skin, globalResources.getShapeShader(), globalResources.getSpriteShader());
            minimapInterface.setFillParent(true);
            minimapInterface.right().top();
            minimapInterface.pad(pad, 0f, 0f, pad);
            interfaces.add(minimapInterface);
        }
        if (show) {
            // Add to ui
            if (!minimapInterface.hasParent() || minimapInterface.getParent() != ui.getRoot()) {
                ui.addActor(minimapInterface);
            }
        } else {
            // Remove from ui
            minimapInterface.remove();
        }
    }

    public void addLoadProgressInterface() {
        loadProgressInterface = new LoadProgressInterface(400f, skin);
        loadProgressInterface.setFillParent(true);
        loadProgressInterface.center().bottom();
        loadProgressInterface.pad(0, 0, 0, 0);
        interfaces.add(loadProgressInterface);
    }

    public void toggleMinimapInterface(Stage stage) {
        showMinimapInterface(stage, minimapInterface == null || (!minimapInterface.isVisible() || !minimapInterface.hasParent()));
    }

    public void showMinimapWindow(Stage stage, boolean show) {
        if (minimapWindow == null)
            minimapWindow = new MinimapWindow(stage, skin, globalResources.getShapeShader(), globalResources.getSpriteShader());
        if (show)
            minimapWindow.show(stage, graphics.getWidth() - minimapWindow.getWidth(), graphics.getHeight() - minimapWindow.getHeight());
        else
            minimapWindow.hide();
    }

    public void toggleMinimapWindow(Stage ui) {
        showMinimapWindow(ui, minimapWindow == null || (!minimapWindow.isVisible() || !minimapWindow.hasParent()));
    }

    @Override
    public boolean updateUnitsPerPixel(float upp) {
        boolean cool = super.updateUnitsPerPixel(upp);
        if (cool) {
            controlsWindow.setPosition(0, graphics.getHeight() * unitsPerPixel - controlsWindow.getHeight());
            controlsWindow.recalculateSize();
            if (stage.getHeight() < controlsWindow.getHeight()) {
                // Collapse
                controlsWindow.collapseInstant();
            }
        }
        return cool;
    }
}
