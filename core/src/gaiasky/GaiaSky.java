/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.Timer.Task;
import gaiasky.data.AssetBean;
import gaiasky.data.OctreeLoader;
import gaiasky.data.attitude.IAttitudeServer;
import gaiasky.data.util.AttitudeLoader;
import gaiasky.data.util.OrbitDataLoader;
import gaiasky.data.util.PointCloudData;
import gaiasky.data.util.SceneLoader;
import gaiasky.data.util.SceneLoader.SceneLoaderParameters;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.gui.*;
import gaiasky.render.ComponentTypes;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.MainPostProcessor;
import gaiasky.render.api.IPostProcessor;
import gaiasky.render.api.IPostProcessor.PostProcessBean;
import gaiasky.render.api.IPostProcessor.RenderType;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.camera.CameraManager;
import gaiasky.scene.camera.CameraManager.CameraMode;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.camera.NaturalCamera;
import gaiasky.scene.system.render.SceneRenderer;
import gaiasky.scene.view.FocusView;
import gaiasky.script.IScriptingInterface;
import gaiasky.script.ScriptingServer;
import gaiasky.util.*;
import gaiasky.util.Logger;
import gaiasky.util.GaiaSkyLoader.GaiaSkyLoaderParameters;
import gaiasky.util.Logger.Log;
import gaiasky.util.ds.GaiaSkyExecutorService;
import gaiasky.util.gdx.g2d.BitmapFont;
import gaiasky.util.gdx.loader.*;
import gaiasky.util.gdx.loader.is.GzipInputStreamProvider;
import gaiasky.util.gdx.loader.is.RegularInputStreamProvider;
import gaiasky.util.gdx.model.IntModel;
import gaiasky.util.gdx.shader.ExtShaderProgram;
import gaiasky.util.gdx.shader.attribute.Attribute;
import gaiasky.util.gdx.shader.loader.AtmosphereShaderProviderLoader;
import gaiasky.util.gdx.shader.loader.GroundShaderProviderLoader;
import gaiasky.util.gdx.shader.loader.RelativisticShaderProviderLoader;
import gaiasky.util.gdx.shader.loader.TessellationShaderProviderLoader;
import gaiasky.util.gdx.shader.provider.*;
import gaiasky.util.gravwaves.RelativisticEffectsManager;
import gaiasky.util.i18n.I18n;
import gaiasky.util.samp.SAMPClient;
import gaiasky.util.screenshot.ScreenshotsManager;
import gaiasky.util.time.GlobalClock;
import gaiasky.util.time.ITimeFrameProvider;
import gaiasky.util.time.RealTimeClock;
import gaiasky.util.tree.OctreeNode;
import gaiasky.vr.openvr.VRContext;
import gaiasky.vr.openvr.VRContext.VRDevice;
import gaiasky.vr.openvr.VRContext.VRDeviceType;
import gaiasky.vr.openvr.VRStatus;
import org.lwjgl.opengl.GL30;
import org.lwjgl.openvr.VR;
import org.lwjgl.openvr.VRCompositor;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main class. Holds all the entities manages the update/draw cycle and all
 * other top-level functions of Gaia Sky.
 */
public class GaiaSky implements ApplicationListener, IObserver {
    private static final Log logger = Logger.getLogger(GaiaSky.class);

    /**
     * Singleton instance.
     **/
    public static GaiaSky instance;
    /**
     * Used to wait for new frames
     */
    public final Object frameMonitor = new Object();
    private final String sceneName = "SceneData";
    /**
     * Set log level to debug
     */
    private final boolean debugMode;
    /**
     * Whether to attempt a connection to the VR HMD
     */
    private final boolean vr;
    /**
     * Headless mode
     */
    private final boolean headless;
    /**
     * Skip welcome screen if possible
     */
    private final boolean skipWelcome;
    /**
     * Forbids the creation of the scripting server
     */
    private final boolean noScripting;
    /**
     * Parked update runnables. Run after the update-scene stage.
     */
    private final Array<Runnable> parkedUpdateRunnables = new Array<>(10);
    private final Map<String, Runnable> parkedUpdateRunnablesMap = Collections.synchronizedMap(new HashMap<>());
    /**
     * Parked camera runnables. Run after the update-camera stage and before the update-scene stage.
     */
    private final Array<Runnable> parkedCameraRunnables = new Array<>(10);
    private final Map<String, Runnable> parkedCameraRunnablesMap = Collections.synchronizedMap(new HashMap<>());
    // Has the application crashed?
    private final AtomicBoolean crashed = new AtomicBoolean(false);
    // Running state
    private final AtomicBoolean running = new AtomicBoolean(true);
    /**
     * Window.
     **/
    public Lwjgl3Window window;
    /**
     * Graphics.
     **/
    public Graphics graphics;
    /**
     * The {@link VRContext} setup in {@link #createVR()}, may be null if no HMD is
     * present or SteamVR is not installed.
     */
    public VRContext vrContext;
    /**
     * Loading frame buffers.
     **/
    public FrameBuffer vrLoadingLeftFb, vrLoadingRightFb;
    /**
     * Loading texture.
     **/
    public org.lwjgl.openvr.Texture vrLoadingLeftTex, vrLoadingRightTex;
    /**
     * The asset manager.
     */
    public AssetManager assetManager;
    /**
     * The main camera manager.
     */
    public CameraManager cameraManager;
    public Scene scene;
    public SceneRenderer sceneRenderer;
    /**
     * Holds the number of frames produced in this session.
     */
    public long frames;
    public InputMultiplexer inputMultiplexer;
    /**
     * The user interfaces
     */
    public IGui welcomeGui, welcomeGuiVR, loadingGui, loadingGuiVR, mainGui, spacecraftGui, stereoGui, debugGui, crashGui, gamepadGui;
    /**
     * Time
     */
    public ITimeFrameProvider time;
    // Window has been created successfully
    public boolean windowCreated = false;
    /**
     * Save state on exit
     */
    public boolean saveState = true;
    /**
     * External view with final rendered scene and no UI
     */
    public boolean externalView;
    /**
     * External UI window
     */
    public GaiaSkyView gaiaSkyView = null;
    /**
     * The scene graph update process.
     */
    private Runnable updateProcess;
    /**
     * Current update-render implementation.
     * One of {@link #runnableInitialGui}, {@link #runnableLoadingGui} or {@link #runnableRender}.
     **/
    private Runnable updateRenderProcess;
    /**
     * Maps the VR devices to model objects.
     */
    private HashMap<VRDevice, Entity> vrDeviceToModel;
    private IPostProcessor postProcessor;
    /**
     * The session start time, in milliseconds.
     */
    private long startTime;
    /**
     * Holds the session run time in seconds.
     */
    private double t;
    private GuiRegistry guiRegistry;
    /**
     * Dynamic resolution level, the index in {@link gaiasky.util.Settings.GraphicsSettings#dynamicResolutionScale}
     * 0 - native
     * 1 - level 1
     * 2 - level 2
     */
    private int dynamicResolutionLevel = 0;
    private long lastDynamicResolutionChange = 0;
    /**
     * Provisional console logger
     */
    private ConsoleLogger consoleLogger;
    /**
     * List of GUIs
     */
    private List<IGui> guis;
    // The sprite batch to render the back buffer to screen
    private SpriteBatch renderBatch;
    /** Settings reference **/
    private Settings settings;
    /**
     * Displays the initial GUI
     **/
    private final Runnable runnableInitialGui = () -> {
        renderGui(welcomeGui);
        if (settings.runtime.openVr) {
            try {
                vrContext.pollEvents();
            } catch (Exception e) {
                logger.error(e);
            }

            renderVRGui((VRGui<?>) welcomeGuiVR);
        }
    };
    /**
     * Camera recording or not?
     */
    private boolean camRecording = false;
    // Gaia Sky has finished initialization
    private boolean initialized = false;
    /**
     * Global resources holder
     */
    private GlobalResources globalResources;
    /**
     * The global catalog manager
     */
    private CatalogManager catalogManager;
    /**
     * The scripting interface
     */
    private IScriptingInterface scripting;
    /**
     * The dataset updater -- sorts and updates dataset metadata
     */
    private GaiaSkyExecutorService executorService;
    /**
     * The bookmarks manager
     */
    private BookmarksManager bookmarksManager;
    /**
     * The SAMP client
     */
    private SAMPClient sampClient;
    private long start = System.currentTimeMillis();
    private long lastResizeTime = Long.MAX_VALUE;
    private int resizeWidth, resizeHeight;
    /**
     * Updates and renders the scene.
     **/
    private final Runnable runnableRender = () -> {

        // Asynchronous load of textures and resources
        assetManager.update();

        if (!settings.runtime.updatePause) {
            synchronized (frameMonitor) {
                frameMonitor.notify();
            }
            /*
             * UPDATE SCENE
             */
            update(graphics.getDeltaTime());


            /*
             * FRAME OUTPUT
             */
            EventManager.publish(Event.RENDER_FRAME, this);

            /*
             * SCREEN OUTPUT
             */
            if (settings.graphics.screenOutput) {
                int tw = graphics.getWidth();
                int th = graphics.getHeight();
                if (tw == 0 || th == 0) {
                    // Hack - on Windows the reported width and height is 0 when the window is minimized
                    tw = settings.graphics.resolution[0];
                    th = settings.graphics.resolution[1];
                }
                int w = (int) (tw * settings.graphics.backBufferScale);
                int h = (int) (th * settings.graphics.backBufferScale);
                /* RENDER THE SCENE */
                sceneRenderer.clearScreen();

                if (settings.runtime.openVr) {
                    sceneRenderer.render(cameraManager, t, settings.graphics.backBufferResolution[0], settings.graphics.backBufferResolution[1], tw, th, null, postProcessor.getPostProcessBean(RenderType.screen));
                } else {
                    PostProcessBean ppb = postProcessor.getPostProcessBean(RenderType.screen);
                    if (ppb != null)
                        sceneRenderer.render(cameraManager, t, w, h, tw, th, null, ppb);
                }

                // Render the GUI, setting the viewport
                if (settings.runtime.openVr) {
                    guiRegistry.render(settings.graphics.backBufferResolution[0], settings.graphics.backBufferResolution[1]);
                } else {
                    guiRegistry.render(tw, th);
                }
            }
        }
        // Clean lists
        sceneRenderer.swapRenderLists();
        // Number of frames
        frames++;

        if (settings.graphics.fpsLimit > 0.0) {
            // If FPS limit is on, dynamic resolution is off
            sleep(settings.graphics.fpsLimit);
        } else if (!settings.program.isStereoOrCubemap() && settings.graphics.dynamicResolution && TimeUtils.millis() - startTime > 10000 && TimeUtils.millis() - lastDynamicResolutionChange > 500 && !settings.runtime.openVr) {
            // Dynamic resolution, adjust the back-buffer scale depending on the frame rate
            float fps = 1f / graphics.getDeltaTime();

            if (fps < 30 && dynamicResolutionLevel < settings.graphics.dynamicResolutionScale.length - 1) {
                // Downscale
                settings.graphics.backBufferScale = settings.graphics.dynamicResolutionScale[++dynamicResolutionLevel];
                postRunnable(() -> resizeImmediate(graphics.getWidth(), graphics.getHeight(), true, true, false, false));
                lastDynamicResolutionChange = TimeUtils.millis();
            } else if (fps > 60 && dynamicResolutionLevel > 0) {
                // Move up
                settings.graphics.backBufferScale = settings.graphics.dynamicResolutionScale[--dynamicResolutionLevel];
                postRunnable(() -> resizeImmediate(graphics.getWidth(), graphics.getHeight(), true, true, false, false));
                lastDynamicResolutionChange = TimeUtils.millis();
            }
        }
    };
    /**
     * Displays the loading GUI
     **/
    private final Runnable runnableLoadingGui = () -> {
        boolean finished = false;
        try {
            finished = assetManager.update();
        } catch (GdxRuntimeException e) {
            // Resource failed to load
            logger.warn(e.getLocalizedMessage());
        }
        if (finished) {
            doneLoading();
            updateRenderProcess = runnableRender;
        } else {
            // Display loading screen
            if (settings.runtime.openVr) {
                renderGui(loadingGui);

                try {
                    vrContext.pollEvents();
                } catch (Exception e) {
                    logger.error(e);
                }
                renderVRGui((VRGui<?>) loadingGuiVR);
            } else {
                renderGui(loadingGui);
            }
        }
    };

    /**
     * Creates an instance of Gaia Sky.
     */
    public GaiaSky() {
        this(false, false, false, false, false, false);
    }

    /**
     * Creates an instance of Gaia Sky.
     *
     * @param skipWelcome  Skips welcome screen if possible.
     * @param vr           Launch in VR mode.
     * @param externalView Open a new window with a view of the rendered scene.
     * @param headless     Launch in headless mode, without window.
     * @param debugMode    Output debug information.
     */
    public GaiaSky(final boolean skipWelcome, final boolean vr, final boolean externalView, final boolean headless, final boolean noScriptingServer, final boolean debugMode) {
        super();

        instance = this;
        this.settings = Settings.settings;

        // Flags
        this.skipWelcome = skipWelcome;
        this.vr = vr;
        this.externalView = externalView;
        this.headless = headless;
        this.noScripting = noScriptingServer;
        this.debugMode = debugMode;

        this.updateRenderProcess = runnableInitialGui;
    }

    /**
     * Posts a runnable that will run once after the current frame.
     *
     * @param r The runnable to post.
     */
    public static synchronized void postRunnable(final Runnable r) {
        if (instance != null && instance.window != null)
            instance.window.postRunnable(r);
        else
            Gdx.app.postRunnable(r);
    }

    @Override
    public void create() {
        startTime = TimeUtils.millis();
        // Set log level.
        Gdx.app.setLogLevel(debugMode ? Application.LOG_DEBUG : Application.LOG_INFO);
        Logger.level = debugMode ? Logger.LoggerLevel.DEBUG : Logger.LoggerLevel.INFO;

        // Initialize timer.
        // Doing this preemptively helps prevent some macOS errors.
        Timer.instance();

        // Console logger.
        consoleLogger = new ConsoleLogger();

        if (debugMode)
            logger.debug("Logging level set to DEBUG");

        // Init graphics and window.
        graphics = Gdx.graphics;
        window = headless ? null : ((Lwjgl3Graphics) graphics).getWindow();

        // Log basic info.
        logger.info(settings.version.version, I18n.msg("gui.build", settings.version.build));
        logger.info(I18n.msg("notif.info.displaymode", graphics.getWidth(), graphics.getHeight(), Gdx.graphics.isFullscreen()));
        logger.info(I18n.msg("notif.info.device", GL30.glGetString(GL30.GL_RENDERER)));
        logger.info(I18n.msg("notif.glversion", GL30.glGetString(GL30.GL_VERSION)));
        logger.info(I18n.msg("notif.glslversion", GL30.glGetString(GL30.GL_SHADING_LANGUAGE_VERSION)));
        logger.info(I18n.msg("notif.javaversion", System.getProperty("java.version"), System.getProperty("java.vendor")));
        logger.info(I18n.msg("notif.info.maxattribs", GL30.glGetInteger(GL30.GL_MAX_VERTEX_ATTRIBS)));
        logger.info(I18n.msg("notif.info.maxtexsize", GL30.glGetInteger(GL30.GL_MAX_TEXTURE_SIZE)));

        // Disable all kinds of input.
        EventManager.publish(Event.INPUT_ENABLED_CMD, this, false);

        if (!settings.initialized) {
            logger.error(new RuntimeException(I18n.msg("notif.error", "global configuration not initialized")));
            return;
        }

        // Initialize times.
        final ITimeFrameProvider clock = new GlobalClock(1, Instant.now());
        final ITimeFrameProvider real = new RealTimeClock();
        time = settings.runtime.realTime ? real : clock;
        t = 0;

        // Initialize i18n.
        I18n.initialize();

        // Tooltips.
        TooltipManager.getInstance().initialTime = 1f;
        TooltipManager.getInstance().hideAll();

        // Initialize asset manager.
        final FileHandleResolver internalResolver = new InternalFileHandleResolver();
        final FileHandleResolver dataResolver = fileName -> settings.data.dataFileHandle(fileName);
        assetManager = new AssetManager(internalResolver);
        assetManager.setLoader(Texture.class, ".pfm", new PFMTextureLoader(dataResolver));
        assetManager.setLoader(PFMData.class, new PFMDataLoader(dataResolver));
        assetManager.setLoader(Pixmap.class, new OwnPixmapLoader(dataResolver));
        assetManager.setLoader(Scene.class, new SceneLoader(dataResolver));
        assetManager.setLoader(PointCloudData.class, new OrbitDataLoader(dataResolver));
        assetManager.setLoader(IAttitudeServer.class, new AttitudeLoader(dataResolver));
        assetManager.setLoader(ExtShaderProgram.class, new ShaderProgramProvider(internalResolver, ".vertex.glsl", ".fragment.glsl"));
        assetManager.setLoader(BitmapFont.class, new BitmapFontLoader(internalResolver));
        assetManager.setLoader(Texture.class, new OwnTextureLoader(internalResolver));
        assetManager.setLoader(AtmosphereShaderProvider.class, new AtmosphereShaderProviderLoader<>(internalResolver));
        assetManager.setLoader(GroundShaderProvider.class, new GroundShaderProviderLoader<>(internalResolver));
        assetManager.setLoader(TessellationShaderProvider.class, new TessellationShaderProviderLoader<>(internalResolver));
        assetManager.setLoader(RelativisticShaderProvider.class, new RelativisticShaderProviderLoader<>(internalResolver));
        assetManager.setLoader(IntModel.class, ".obj", new OwnObjLoader(new RegularInputStreamProvider(), internalResolver));
        assetManager.setLoader(IntModel.class, ".obj.gz", new OwnObjLoader(new GzipInputStreamProvider(), internalResolver));
        assetManager.setLoader(IntModel.class, ".g3dj", new G3dModelLoader(new JsonReader(), internalResolver));
        assetManager.setLoader(IntModel.class, ".g3db", new G3dModelLoader(new UBJsonReader(), internalResolver));
        assetManager.setLoader(GaiaSkyAssets.class, new GaiaSkyLoader(internalResolver));

        // Init global resources -- Can't be postponed!
        this.globalResources = new GlobalResources(assetManager);

        // Catalog manager.
        this.catalogManager = new CatalogManager();

        // Initialise master manager.
        MasterManager.initialize();
        // Load slave assets.
        SlaveManager.load(assetManager);

        // Initialise dataset updater.
        this.executorService = new GaiaSkyExecutorService();

        // Initialise Cameras.
        cameraManager = new CameraManager(assetManager, CameraMode.FOCUS_MODE, vr, globalResources);

        // Set asset manager to asset bean.
        AssetBean.setAssetManager(assetManager);

        // Create vr context if possible.
        final VRStatus vrStatus = createVR();
        cameraManager.updateFrustumPlanes();

        // GUI.
        guis = new ArrayList<>(3);

        // Post-processor.
        postProcessor = new MainPostProcessor(null);

        // Scene renderer.
        sceneRenderer = new SceneRenderer(vrContext, globalResources);
        sceneRenderer.initialize(assetManager);

        // Screenshots and frame output manager.
        new ScreenshotsManager(this, sceneRenderer, globalResources);

        // Load various assets.
        assetManager.load("gaiasky-assets", GaiaSkyAssets.class, new GaiaSkyLoaderParameters(this, noScripting));

        // Tell the asset manager to load all the assets.
        final Set<AssetBean> assets = AssetBean.getAssets();
        for (AssetBean ab : assets) {
            ab.load(assetManager);
        }

        renderBatch = globalResources.getSpriteBatch();

        EventManager.instance.subscribe(this, Event.LOAD_DATA_CMD);

        welcomeGui = new WelcomeGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale, skipWelcome, vrStatus);
        welcomeGui.initialize(assetManager, globalResources.getSpriteBatch());
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(welcomeGui.getGuiStage());
        Gdx.input.setInputProcessor(inputMultiplexer);

        if (settings.runtime.openVr) {
            welcomeGuiVR = new VRGui<>(WelcomeGuiVR.class, (int) (settings.graphics.backBufferResolution[0] / 2f), globalResources.getSkin(), graphics, 1f / settings.program.ui.scale);
            welcomeGuiVR.initialize(assetManager, globalResources.getSpriteBatch());
        }
    }

    /**
     * Attempt to create a VR context. This operation will only succeed if an HMD is connected
     * and detected via OpenVR.
     **/
    private VRStatus createVR() {
        if (vr) {
            // Initializing the VRContext may fail if no HMD is connected or SteamVR
            // is not installed.
            try {
                //OpenVRQuery.queryOpenVr();
                settings.runtime.openVr = true;
                Constants.initialize(settings.scene.distanceScaleVr);

                vrContext = new VRContext();
                vrContext.pollEvents();

                final VRDevice hmd = vrContext.getDeviceByType(VRDeviceType.HeadMountedDisplay);
                logger.info("Initialization of VR successful");
                if (hmd == null) {
                    logger.info("HMD device is null!");
                } else {
                    logger.info("HMD device is not null: " + vrContext.getDeviceByType(VRDeviceType.HeadMountedDisplay).toString());
                }

                vrDeviceToModel = new HashMap<>();

                if (settings.graphics.resolution[0] != vrContext.getWidth()) {
                    logger.info("Warning, resizing according to VRSystem values:  [" + settings.graphics.resolution[0] + "x" + settings.graphics.resolution[1] + "] -> [" + vrContext.getWidth() + "x" + vrContext.getHeight() + "]");
                    // Do not resize the screen!
                    settings.graphics.backBufferResolution[1] = vrContext.getHeight();
                    settings.graphics.backBufferResolution[0] = vrContext.getWidth();
                }
                settings.graphics.vsync = false;

                graphics.setWindowedMode(settings.graphics.resolution[0], settings.graphics.resolution[1]);
                graphics.setVSync(settings.graphics.vsync);

                vrLoadingLeftFb = new FrameBuffer(Format.RGBA8888, vrContext.getWidth(), vrContext.getHeight(), true);
                vrLoadingLeftTex = org.lwjgl.openvr.Texture.create();
                vrLoadingLeftTex.set(vrLoadingLeftFb.getColorBufferTexture().getTextureObjectHandle(), VR.ETextureType_TextureType_OpenGL, VR.EColorSpace_ColorSpace_Gamma);

                vrLoadingRightFb = new FrameBuffer(Format.RGBA8888, vrContext.getWidth(), vrContext.getHeight(), true);
                vrLoadingRightTex = org.lwjgl.openvr.Texture.create();
                vrLoadingRightTex.set(vrLoadingRightFb.getColorBufferTexture().getTextureObjectHandle(), VR.ETextureType_TextureType_OpenGL, VR.EColorSpace_ColorSpace_Gamma);

                // Sprite batch for VR - uses back buffer resolution.
                globalResources.setSpriteBatchVR(new SpriteBatch(500, globalResources.getSpriteShader()));
                globalResources.getSpriteBatchVR().getProjectionMatrix().setToOrtho2D(0, 0, settings.graphics.backBufferResolution[0], settings.graphics.backBufferResolution[1]);

                // Enable visibility of 'Others' if off (for VR controllers).
                if (!settings.scene.visibility.get(ComponentType.Others.name())) {
                    EventManager.publish(Event.TOGGLE_VISIBILITY_CMD, this, "element.others", false);
                }
                return VRStatus.OK;
            } catch (Exception e) {
                // If initializing the VRContext failed.
                settings.runtime.openVr = false;
                logger.error(e);
                logger.error("Initialisation of VR context failed");
                return VRStatus.ERROR_NO_CONTEXT;
            }
        } else {
            // Desktop mode.
            settings.runtime.openVr = false;
            Constants.initialize(settings.scene.distanceScaleDesktop);
        }
        return VRStatus.NO_VR;
    }

    /**
     * Execute this when the models have finished loading. This sets the models
     * to their classes and removes the Loading message.
     */
    private void doneLoading() {
        // Get assets.
        final var assets = assetManager.get("gaiasky-assets", GaiaSkyAssets.class);

        windowCreated = true;
        // Dispose of initial and loading GUIs.
        welcomeGui.dispose();
        welcomeGui = null;

        loadingGui.dispose();
        loadingGui = null;

        // Dispose vr loading GUI.
        if (settings.runtime.openVr) {
            welcomeGuiVR.dispose();
            welcomeGuiVR = null;

            loadingGuiVR.dispose();
            loadingGuiVR = null;

            vrLoadingLeftTex.clear();
            vrLoadingLeftFb.dispose();
            vrLoadingLeftTex = null;
            vrLoadingLeftFb = null;

            vrLoadingRightTex.clear();
            vrLoadingRightFb.dispose();
            vrLoadingRightTex = null;
            vrLoadingRightFb = null;
        }

        // Collect assets.
        scripting = assets.scriptingInterface;
        bookmarksManager = assets.bookmarksManager;
        sampClient = assets.sampClient;

        /*
         * Complete post-processor loading.
         */
        postProcessor.doneLoading(assetManager);

        /*
         * Fetch scene object.
         */
        if (assetManager.isLoaded(sceneName)) {
            scene = assetManager.get(sceneName);
        } else {
            throw new RuntimeException("Error loading scene from data load string: " + sceneName + ", and files: " + TextUtils.concatenate(File.pathSeparator, settings.data.dataFiles));
        }


        /*
         * Implement update process.
         */
        updateProcess = () -> {
            // Update scene.
            scene.update(time);

            // Swap proximity buffers.
            cameraManager.swapBuffers();
        };

        /*
         * Complete scene renderer loading.
         */
        sceneRenderer.doneLoading(assetManager);
        sceneRenderer.resize(graphics.getWidth(), graphics.getHeight(), (int) Math.round(graphics.getWidth() * settings.graphics.backBufferScale), (int) Math.round(graphics.getHeight() * settings.graphics.backBufferScale));

        // Set up entities.
        scene.setUpEntities();
        // Prepare scene for update.
        scene.prepareUpdateSystems(sceneRenderer);

        // Initialize input multiplexer to handle various input processors.
        inputMultiplexer.clear();
        guiRegistry = new GuiRegistry(this.globalResources.getSkin(), this.scene, this.catalogManager);
        guiRegistry.setInputMultiplexer(inputMultiplexer);
        Gdx.input.setInputProcessor(inputMultiplexer);

        // Stop updating log list.
        consoleLogger.setUseHistorical(false);

        // Init GUIs, step 2.
        reinitialiseGUI2();

        // Publish visibility.
        EventManager.publish(Event.VISIBILITY_OF_COMPONENTS, this, sceneRenderer.visible);

        // Key bindings.
        inputMultiplexer.addProcessor(new KeyboardInputController(Gdx.input));

        // Broadcast scene.
        EventManager.publish(Event.SCENE_LOADED, this, scene);

        touchSceneGraph();

        // Initialise time in GUI.
        EventManager.publish(Event.TIME_CHANGE_INFO, this, time.getTime());

        // Subscribe to events.
        EventManager.instance.subscribe(this, Event.RECORD_CAMERA_CMD,
                Event.CAMERA_MODE_CMD, Event.STEREOSCOPIC_CMD, Event.CUBEMAP_CMD,
                Event.PARK_RUNNABLE, Event.PARK_CAMERA_RUNNABLE, Event.UNPARK_RUNNABLE, Event.SCENE_ADD_OBJECT_CMD, Event.SCENE_ADD_OBJECT_NO_POST_CMD,
                Event.SCENE_REMOVE_OBJECT_CMD, Event.SCENE_REMOVE_OBJECT_NO_POST_CMD, Event.SCENE_RELOAD_NAMES_CMD, Event.HOME_CMD,
                Event.UI_SCALE_CMD, Event.RESET_RENDERER, Event.SCENE_FORCE_UPDATE);

        // Re-enable input.
        EventManager.publish(Event.INPUT_ENABLED_CMD, this, true);

        // Set current date.
        EventManager.publish(Event.TIME_CHANGE_CMD, this, Instant.now());

        // Resize GUIs to current size.
        for (IGui gui : guis)
            gui.resize(graphics.getWidth(), graphics.getHeight());

        if (settings.runtime.openVr) {
            // Resize post-processors and render systems.
            postRunnable(() -> resizeImmediate(vrContext.getWidth(), vrContext.getHeight(), true, false, false, false));
        }

        // Initialise frames.
        frames = 0;

        // Debug info scheduler.
        final Task debugTask1 = new Task() {
            @Override
            public void run() {
                // FPS.
                EventManager.publish(Event.FPS_INFO, this, 1f / graphics.getDeltaTime());
                // Current session time.
                EventManager.publish(Event.DEBUG_TIME, this, TimeUtils.timeSinceMillis(startTime) / 1000d);
                // Memory.
                EventManager.publish(Event.DEBUG_RAM, this, MemInfo.getUsedMemory(), MemInfo.getFreeMemory(), MemInfo.getTotalMemory(), MemInfo.getMaxMemory());
                // VRAM.
                EventManager.publish(Event.DEBUG_VRAM, this, VMemInfo.getUsedMemory(), VMemInfo.getTotalMemory());
                // Threads.
                EventManager.publish(Event.DEBUG_THREADS, this, executorService.pool().getActiveCount(), executorService.pool().getPoolSize());
                // Dynamic resolution.
                EventManager.publish(Event.DEBUG_DYN_RES, this, dynamicResolutionLevel, settings.graphics.dynamicResolutionScale[dynamicResolutionLevel]);
                // Octree objects.
                if (OctreeLoader.instance != null) {
                    // Observed objects.
                    EventManager.publish(Event.DEBUG_OBJECTS, this, OctreeNode.nObjectsObserved, OctreeLoader.instance.getNLoadedStars());
                    // Observed octants.
                    EventManager.publish(Event.DEBUG_QUEUE, this, OctreeNode.nOctantsObserved, OctreeLoader.instance.getLoadQueueSize());
                }
            }
        };

        final Task debugTask10 = new Task() {
            @Override
            public void run() {
                EventManager.publish(Event.SAMP_INFO, this, sampClient.getStatus());
            }
        };

        // Every second.
        Timer.schedule(debugTask1, 2, 1);
        // Every 10 seconds.
        Timer.schedule(debugTask10, 2, 10);

        // Start capturing locations.
        final Task startCapturing = new Task() {
            @Override
            public void run() {
                LocationLogManager.instance().startCapturing();
            }
        };
        Timer.schedule(startCapturing, 1f);

        // Release notes.
        guiRegistry.publishReleaseNotes();

        // Go home.
        goHome();

        // Log attributes.
        final Task logAttributes = new Task() {
            @Override
            public void run() {
                logger.info("Total number of attributes registered: " + Attribute.getNumAttributes());
                if (Settings.settings.program.debugInfo) {
                    logger.debug("Registered attributes:");
                    Array<String> attributes = Attribute.getTypes();
                    for (int i = 0; i < attributes.size; i++) {
                        logger.debug(i + ": " + attributes.get(i));
                    }
                }
            }
        };
        Timer.schedule(logAttributes, 5);

        // Initial report.
        scene.reportDebugObjects();

        // Initialized.
        EventManager.publish(Event.INITIALIZED_INFO, this);
        sceneRenderer.setRendering(true);
        initialized = true;
    }

    /**
     * Forces a global scene update.
     * Updates the scene with a very small dt to force
     * the re-computation of all entities.
     */
    public void touchSceneGraph() {
        // Update whole tree to initialize positions.
        settings.runtime.octreeLoadActive = false;
        boolean timeOnBak = settings.runtime.timeOn;
        settings.runtime.timeOn = true;
        // Set non-zero time to force update the positions.
        time.update(1e-9);

        scene.update(time);

        // Clear render lists.
        sceneRenderer.swapRenderLists();
        // Time back to zero.
        time.update(0);
        settings.runtime.timeOn = timeOnBak;
        settings.runtime.octreeLoadActive = true;
    }

    /**
     * Moves the camera home. That is either the Earth, if it exists, or somewhere close to the Sun.
     */
    private void goHome() {
        final Entity homeObject = scene.findFocus(settings.scene.homeObject);
        boolean isOn = true;
        if (homeObject != null && (isOn = isOn(Mapper.base.get(homeObject).ct)) && !settings.program.net.slave.active) {
            // Set focus to Earth.
            EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraMode.FOCUS_MODE);
            EventManager.publish(Event.FOCUS_CHANGE_CMD, this, homeObject, true);
            EventManager.publish(Event.GO_TO_OBJECT_CMD, this);
            if (settings.runtime.openVr) {
                // Free mode by default in VR.
                EventManager.publishDelayed(Event.CAMERA_MODE_CMD, this, 1000L, CameraMode.FREE_MODE);
            }
        } else {
            // At 5 AU in Y looking towards origin (top-down look).
            EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraMode.FREE_MODE);
            EventManager.publish(Event.CAMERA_POS_CMD, this, (Object) new double[] { 0d, 5d * Constants.AU_TO_U, 0d });
            EventManager.publish(Event.CAMERA_DIR_CMD, this, (Object) new double[] { 0d, -1d, 0d });
            EventManager.publish(Event.CAMERA_UP_CMD, this, (Object) new double[] { 0d, 0d, 1d });
        }

        if (!isOn) {
            Task t = new Task() {
                @Override
                public void run() {
                    logger.info("The home object '" + settings.scene.homeObject + "' is invisible due to its type(s): " + Mapper.base.get(homeObject).ct);
                }
            };
            Timer.schedule(t, 1);
        }
    }

    /**
     * Re-initialises all the GUI (step 1).
     */
    public void reinitialiseGUI1() {
        if (guis != null && !guis.isEmpty()) {
            for (IGui gui : guis)
                gui.dispose();
            guis.clear();
        }

        mainGui = new FullGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale, globalResources, catalogManager);
        mainGui.initialize(assetManager, globalResources.getSpriteBatch());

        debugGui = new DebugGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale);
        debugGui.initialize(assetManager, globalResources.getSpriteBatch());

        spacecraftGui = new SpacecraftGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale);
        spacecraftGui.initialize(assetManager, globalResources.getSpriteBatch());

        stereoGui = new StereoGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale);
        stereoGui.initialize(assetManager, globalResources.getSpriteBatch());

        gamepadGui = new GamepadGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale);
        gamepadGui.initialize(assetManager, globalResources.getSpriteBatch());

        if (guis != null) {
            guis.add(mainGui);
            guis.add(debugGui);
            guis.add(spacecraftGui);
            guis.add(stereoGui);
            guis.add(gamepadGui);
        }
    }

    /**
     * Second step in GUI initialisation.
     */
    public void reinitialiseGUI2() {
        // Reinitialise registry to listen to relevant events.
        if (guiRegistry != null)
            guiRegistry.dispose();
        guiRegistry = new GuiRegistry(globalResources.getSkin(), scene, catalogManager);
        guiRegistry.setInputMultiplexer(inputMultiplexer);

        // Unregister all current GUIs.
        guiRegistry.unregisterAll();

        // Set scene to main gui.
        ((FullGui) mainGui).setScene(scene);
        mainGui.setVisibilityToggles(ComponentType.values(), sceneRenderer.visible);

        for (IGui gui : guis)
            gui.doneLoading(assetManager);

        if (settings.program.modeStereo.active) {
            guiRegistry.set(stereoGui);
            guiRegistry.setPrevious(mainGui);
        } else {
            guiRegistry.set(mainGui);
            guiRegistry.setPrevious(null);
        }
        guiRegistry.registerGui(debugGui);
        guiRegistry.addProcessor(debugGui);

        guiRegistry.registerGui(gamepadGui);
        guiRegistry.addProcessor(gamepadGui);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        // Stop
        running.set(false);

        // Revert back-buffer resolution.
        if (dynamicResolutionLevel > 0 && settings.graphics.backBufferScale == settings.graphics.dynamicResolutionScale[0]) {
            settings.graphics.backBufferScale = 1f;
            dynamicResolutionLevel = 0;
        }

        // Dispose.
        if (saveState && !crashed.get()) {
            SettingsManager.persistSettings(new File(System.getProperty("properties.file")));
            if (bookmarksManager != null)
                bookmarksManager.persistBookmarks();
        }

        if (vrContext != null)
            vrContext.dispose();

        ScriptingServer.dispose();

        // Flush frames.
        EventManager.publish(Event.FLUSH_FRAMES, this);

        // Dispose all.
        if (guis != null)
            for (IGui gui : guis)
                gui.dispose();

        EventManager.publish(Event.DISPOSE, this);
        ModelCache.cache.dispose();

        // Shutdown asset manager.
        if (assetManager != null) {
            assetManager.dispose();
        }

        // Shutdown dataset updater thread pool.
        if (executorService != null) {
            executorService.shutDownThreadPool();
        }

        // Scripting.
        ScriptingServer.dispose();

        // Renderer.
        if (sceneRenderer != null) {
            sceneRenderer.dispose();
        }

        // Post processor.
        if (postProcessor != null) {
            postProcessor.dispose();
        }

        // Dispose music manager.
        MusicManager.dispose();

        // Clear temp.
        try {
            Path tmp = SysUtils.getTempDir(settings.data.location);
            if (java.nio.file.Files.exists(tmp) && java.nio.file.Files.isDirectory(tmp))
                GlobalResources.deleteRecursively(tmp);
        } catch (Exception e) {
            logger.error(e, "Error deleting tmp directory");
        }
    }

    public void resetDynamicResolution() {
        dynamicResolutionLevel = 0;
        settings.graphics.backBufferScale = 1f;
        postRunnable(() -> resizeImmediate(graphics.getWidth(), graphics.getHeight(), true, true, false, false));
        lastDynamicResolutionChange = 0;
    }

    public FrameBuffer getBackRenderBuffer() {
        return sceneRenderer.getRenderProcess().getResultBuffer();
    }

    private void renderVRGui(VRGui<?> vrGui) {
        vrLoadingLeftFb.begin();
        renderGui((vrGui).left());
        vrLoadingLeftFb.end();

        vrLoadingRightFb.begin();
        renderGui((vrGui).right());
        vrLoadingRightFb.end();

        /* Submit to VR compositor. */
        VRCompositor.VRCompositor_Submit(VR.EVREye_Eye_Left, vrLoadingLeftTex, null, VR.EVRSubmitFlags_Submit_Default);
        VRCompositor.VRCompositor_Submit(VR.EVREye_Eye_Right, vrLoadingRightTex, null, VR.EVRSubmitFlags_Submit_Default);
    }

    public void setCrashed(boolean crashed) {
        this.crashed.set(crashed);
    }

    @Override
    public void render() {
        try {
            if (running.get() && !crashed.get()) {
                // Run the render process.
                updateRenderProcess.run();

            } else if (crashGui != null) {
                // Crash information.
                renderGui(crashGui);
            }
        } catch (Throwable t) {
            // Report the crash.
            CrashReporter.reportCrash(t, logger);
            // Set up crash window.
            crashGui = new CrashGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale, t);
            crashGui.initialize(assetManager, globalResources.getSpriteBatch());
            Gdx.input.setInputProcessor(crashGui.getGuiStage());
            // Flag up.
            crashed.set(true);
        }

        // Create UI window if needed.
        if (externalView && gaiaSkyView == null) {
            postRunnable(() -> {
                // Create window.
                Lwjgl3Application app = (Lwjgl3Application) Gdx.app;
                Lwjgl3WindowConfiguration config = new Lwjgl3WindowConfiguration();
                config.setWindowPosition(0, 0);
                config.setWindowedMode(graphics.getWidth(), graphics.getHeight());
                config.setTitle(Settings.APPLICATION_NAME + " - External view");
                config.useVsync(false);
                config.setWindowIcon(Files.FileType.Internal, "icon/gs_icon.png");
                gaiaSkyView = new GaiaSkyView(globalResources.getSkin(), globalResources.getSpriteShader());
                Lwjgl3Window newWindow = app.newWindow(gaiaSkyView, config);
                gaiaSkyView.setWindow(newWindow);
            });
        }
    }

    /**
     * Pause the main thread for a certain amount of time to match the
     * given target frame rate.
     *
     * @param fps The target frame rate.
     */
    private void sleep(double fps) {
        if (fps > 0) {
            long diff = System.currentTimeMillis() - start;
            long targetDelay = Math.round((1000.0 / fps));
            if (diff < targetDelay) {
                try {
                    Thread.sleep(targetDelay - diff);
                } catch (InterruptedException ignored) {
                }
            }
            start = System.currentTimeMillis();
        }
    }

    /**
     * Update method.
     *
     * @param dt Delta time in seconds.
     */
    public void update(double dt) {
        // Resize if needed.
        updateResize();

        Timer.instance();
        // The actual frame time difference in seconds.
        double dtGs;
        if (settings.frame.active) {
            // If frame output is active, we need to set our delta t according to
            // the configured frame rate of the frame output system.
            dtGs = 1.0 / settings.frame.targetFps;
        } else if (camRecording) {
            // If Camera is recording, we need to set our delta t according to
            // the configured frame rate of the camrecorder.
            dtGs = 1.0 / settings.camrecorder.targetFps;
        } else {
            // Max time step is 0.05 seconds (20 FPS). Not in RENDER_OUTPUT MODE.
            dtGs = Math.min(dt, 0.05);
        }

        this.t += dtGs;

        // Update GUI.
        guiRegistry.update(dtGs);
        EventManager.publish(Event.UPDATE_GUI, this, dtGs);

        // Update clock.
        time.update(dtGs);

        // Update delayed events.
        EventManager.instance.dispatchDelayedMessages();

        // Update cameras.
        cameraManager.update(dtGs, time);

        // Run parked update-scene runnables.
        runParkedProcesses(parkedCameraRunnables);
        // If there were runnables, update the perspective camera.
        if (parkedCameraRunnables.size > 0) {
            // Update camera.
            if (cameraManager.current instanceof NaturalCamera) {
                ((NaturalCamera) cameraManager.current).updatePerspectiveCamera();
            }
        }

        // Update GravWaves params
        RelativisticEffectsManager.getInstance().update(time, cameraManager.current);

        // Update scene graph in a thread (sync for now).
        updateProcess.run();

        // Run parked update-scene runnables.
        runParkedProcesses(parkedUpdateRunnables);

    }

    /**
     * Runs the parked processes in the given list.
     *
     * @param processes The list of processes to run.
     */
    private void runParkedProcesses(final Array<Runnable> processes) {
        if (processes != null && processes.size > 0) {
            Iterator<Runnable> it = processes.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                try {
                    r.run();
                } catch (Exception e) {
                    logger.error(e);
                    // If it crashed, remove it.
                    it.remove();
                }
            }
        }
    }

    @Override
    public void resize(final int width, final int height) {
        if (width != 0 && height != 0) {
            if (!initialized) {
                resizeImmediate(width, height, true, true, true, true);
            }

            resizeWidth = width;
            resizeHeight = height;
            lastResizeTime = System.currentTimeMillis();

            if (renderBatch != null)
                renderBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);

            Settings.settings.graphics.resolution[0] = width;
            Settings.settings.graphics.resolution[1] = height;
        }
    }

    private void updateResize() {
        long currResizeTime = System.currentTimeMillis();
        if (currResizeTime - lastResizeTime > 100L) {
            resizeImmediate(resizeWidth, resizeHeight, !settings.runtime.openVr, !settings.runtime.openVr, true, true);
            lastResizeTime = Long.MAX_VALUE;
        }
    }

    public void resizeImmediate(final int width, final int height, boolean resizePostProcessors, boolean resizeRenderSys, boolean resizeGuis, boolean resizeScreenConf) {
        try {
            final int renderWidth = (int) Math.round(width * settings.graphics.backBufferScale);
            final int renderHeight = (int) Math.round(height * settings.graphics.backBufferScale);

            // Resize global UI sprite batch
            globalResources.getSpriteBatch().getProjectionMatrix().setToOrtho2D(0, 0, renderWidth, renderHeight);

            if (!initialized) {
                if (welcomeGui != null)
                    welcomeGui.resize(width, height);
                if (loadingGui != null)
                    loadingGui.resizeImmediate(width, height);
            } else {
                if (resizePostProcessors) {
                    postProcessor.resizeImmediate(renderWidth, renderHeight, width, height);
                }

                if (resizeGuis) {
                    for (IGui gui : guis) {
                        gui.resizeImmediate(width, height);
                    }
                }

                sceneRenderer.resize(width, height, renderWidth, renderHeight, resizeRenderSys);

                if (resizeScreenConf)
                    settings.graphics.resize(width, height);
            }

            cameraManager.updateAngleEdge(renderWidth, renderHeight);
            cameraManager.resize(width, height);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * Renders a particular GUI.
     *
     * @param gui The GUI to render.
     */
    private void renderGui(final IGui gui) {
        gui.update(graphics.getDeltaTime());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        gui.render(graphics.getWidth(), graphics.getHeight());
    }

    public HashMap<VRDevice, Entity> getVRDeviceToModel() {
        return vrDeviceToModel;
    }

    public IScriptingInterface scripting() {
        return this.scripting;
    }

    public GaiaSkyExecutorService getExecutorService() {
        return this.executorService;
    }

    public GuiRegistry getGuiRegistry() {
        return this.guiRegistry;
    }

    public BookmarksManager getBookmarksManager() {
        return this.bookmarksManager;
    }

    public ICamera getICamera() {
        return this.cameraManager.current;
    }

    public double getT() {
        return this.t;
    }

    public CameraManager getCameraManager() {
        return this.cameraManager;
    }

    public IPostProcessor getPostProcessor() {
        return this.postProcessor;
    }

    public boolean isOn(final int ordinal) {
        return this.sceneRenderer.isOn(ordinal);
    }

    public boolean isOn(final ComponentType comp) {
        return this.sceneRenderer.isOn(comp);
    }

    public boolean isOn(final ComponentTypes cts) {
        return this.sceneRenderer.allOn(cts);
    }

    public float alpha(final ComponentTypes cts) {
        return this.sceneRenderer.alpha(cts);
    }

    public Optional<CatalogInfo> getCatalogInfoFromEntity(Entity entity) {
        if (Mapper.datasetDescription.has(entity)) {
            return catalogManager.getByEntity(entity);
        }
        return Optional.empty();
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        switch (event) {
        case LOAD_DATA_CMD -> { // Init components that need assets in data folder.
            reinitialiseGUI1();
            postProcessor.initialize(assetManager);

            // Initialise loading screen.
            loadingGui = new LoadingGui(globalResources.getSkin(), graphics, 1f / settings.program.ui.scale, false);
            loadingGui.initialize(assetManager, globalResources.getSpriteBatch());
            Gdx.input.setInputProcessor(loadingGui.getGuiStage());

            // Also VR.
            if (settings.runtime.openVr) {
                loadingGuiVR = new VRGui<>(LoadingGui.class, (int) (settings.graphics.backBufferResolution[0] / 4f), globalResources.getSkin(), graphics, 1f / settings.program.ui.scale);
                loadingGuiVR.initialize(assetManager, globalResources.getSpriteBatch());
            }
            this.updateRenderProcess = runnableLoadingGui;

            // Load scene.
            if (scene == null) {
                final String[] dataFilesToLoad = new String[settings.data.dataFiles.size()];
                int i = 0;
                // Add data files.
                // Our resolver in the SGLoader itself will resolve their full paths.
                for (String dataFile : settings.data.dataFiles) {
                    dataFilesToLoad[i] = dataFile;
                    i++;
                }
                assetManager.load(sceneName, Scene.class, new SceneLoaderParameters(dataFilesToLoad));
            }
        }
        case RECORD_CAMERA_CMD -> {
            if (data != null && data.length > 0) {
                camRecording = (Boolean) data[0];
            } else {
                camRecording = !camRecording;
            }
        }
        case CAMERA_MODE_CMD -> { // Register/unregister GUI.
            final CameraMode mode = (CameraMode) data[0];
            if (settings.program.modeStereo.isStereoHalfViewport()) {
                guiRegistry.change(stereoGui);
            } else if (mode == CameraMode.SPACECRAFT_MODE) {
                guiRegistry.change(spacecraftGui);
            } else {
                guiRegistry.change(mainGui);
            }
        }
        case STEREOSCOPIC_CMD -> {
            final boolean stereoMode = (Boolean) data[0];
            if (stereoMode && guiRegistry.current != stereoGui) {
                guiRegistry.change(stereoGui);
            } else if (!stereoMode && guiRegistry.previous != stereoGui) {
                IGui prev = guiRegistry.current != null ? guiRegistry.current : mainGui;
                guiRegistry.change(guiRegistry.previous, prev);
            }

            // Disable dynamic resolution.
            // Post a message to the screen.
            if (stereoMode) {
                resetDynamicResolution();

                String[] keysStrToggle = KeyBindings.instance.getStringArrayKeys("action.toggle/element.stereomode");
                String[] keysStrProfile = KeyBindings.instance.getStringArrayKeys("action.switchstereoprofile");
                final ModePopupInfo mpi = new ModePopupInfo();
                mpi.title = I18n.msg("gui.stereo.title");
                mpi.header = I18n.msg("gui.stereo.notice.header");
                ;
                mpi.addMapping(I18n.msg("gui.stereo.notice.back"), keysStrToggle);
                mpi.addMapping(I18n.msg("gui.stereo.notice.profile"), keysStrProfile);

                EventManager.publish(Event.MODE_POPUP_CMD, this, mpi, "stereo", 10f);
            } else {
                EventManager.publish(Event.MODE_POPUP_CMD, this, null, "stereo");
            }
        }
        case CUBEMAP_CMD -> {
            boolean cubemapMode = (Boolean) data[0];
            if (cubemapMode) {
                resetDynamicResolution();
            }
        }
        case SCENE_ADD_OBJECT_CMD -> {
            final Entity toAdd = (Entity) data[0];
            boolean addToIndex = data.length == 1 || (Boolean) data[1];
            if (scene != null) {
                postRunnable(() -> {
                    try {
                        scene.insert(toAdd, addToIndex);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                });
            }
        }
        case SCENE_ADD_OBJECT_NO_POST_CMD -> {
            boolean addToIndex;
            final Entity toAddPost = (Entity) data[0];
            addToIndex = data.length == 1 || (Boolean) data[1];
            if (scene != null) {
                try {
                    scene.insert(toAddPost, addToIndex);
                } catch (Exception e) {
                    logger.error(e);
                }
            }
        }
        case SCENE_REMOVE_OBJECT_CMD -> {
            Entity toRemove = null;
            if (data[0] instanceof String) {
                toRemove = scene.getEntity((String) data[0]);
                if (toRemove == null)
                    return;
            } else if (data[0] instanceof Entity) {
                toRemove = (Entity) data[0];
            } else if (data[0] instanceof FocusView) {
                toRemove = ((FocusView) data[0]).getEntity();
            }
            if (toRemove != null) {
                boolean removeFromIndex = data.length == 1 || (Boolean) data[1];
                if (scene != null) {
                    final Entity entityToRemove = toRemove;
                    postRunnable(() -> {
                        try {
                            scene.remove(entityToRemove, removeFromIndex);
                        } catch (Exception e) {
                            logger.warn(e);
                        }
                    });
                }
            }
        }
        case SCENE_REMOVE_OBJECT_NO_POST_CMD -> {
            Entity toRemove;
            toRemove = null;
            if (data[0] instanceof String) {
                toRemove = scene.getEntity((String) data[0]);
                if (toRemove == null)
                    return;
            } else if (data[0] instanceof Entity) {
                toRemove = (Entity) data[0];
            } else if (data[0] instanceof FocusView) {
                toRemove = ((FocusView) data[0]).getEntity();
            }
            if (toRemove != null) {
                boolean removeFromIndex = data.length == 1 || (Boolean) data[1];
                if (scene != null) {
                    scene.remove(toRemove, removeFromIndex);
                }
            }
        }
        case SCENE_RELOAD_NAMES_CMD -> postRunnable(() -> {
            scene.updateLocalizedNames();
        });
        case UI_SCALE_CMD -> {
            if (guis != null) {
                float uiScale = (Float) data[0];
                for (IGui gui : guis) {
                    gui.updateUnitsPerPixel(1f / uiScale);
                }
            }
        }
        case HOME_CMD -> goHome();
        case PARK_RUNNABLE -> {
            String key = (String) data[0];
            final Runnable updateRunnable = (Runnable) data[1];
            parkUpdateRunnable(key, updateRunnable);
        }
        case PARK_CAMERA_RUNNABLE -> {
            String key;
            key = (String) data[0];
            final Runnable cameraRunnable = (Runnable) data[1];
            parkCameraRunnable(key, cameraRunnable);
        }
        case UNPARK_RUNNABLE -> {
            String key;
            key = (String) data[0];
            removeRunnable(key);
        }
        case RESET_RENDERER -> {
            if (sceneRenderer != null) {
                sceneRenderer.resetRenderSystemFlags();
            }
        }
        case SCENE_FORCE_UPDATE -> touchSceneGraph();
        }

    }

    public boolean isInitialised() {
        return initialized;
    }

    public boolean isHeadless() {
        return headless;
    }

    public GlobalResources getGlobalResources() {
        return this.globalResources;
    }

    public CatalogManager getCatalogManager() {
        return this.catalogManager;
    }

    /**
     * Parks an update runnable that runs after the update-scene stage until it is removed.
     *
     * @param key      The key to identify the runnable.
     * @param runnable The runnable to park.
     */
    public void parkUpdateRunnable(final String key, final Runnable runnable) {
        parkRunnable(key, runnable, parkedUpdateRunnablesMap, parkedUpdateRunnables);
    }

    /**
     * Parks a camera runnable that runs after the update-camera stage, and
     * before the update-scene stage, until it is removed.
     *
     * @param key      The key to identify the runnable.
     * @param runnable The runnable to park.
     */
    public void parkCameraRunnable(final String key, final Runnable runnable) {
        parkRunnable(key, runnable, parkedCameraRunnablesMap, parkedCameraRunnables);
    }

    /**
     * Parks a runnable to the given map and list.
     *
     * @param key       The key to identify the runnable.
     * @param runnable  The runnable to park.
     * @param map       The map to use.
     * @param runnables The runnables list.
     */
    public void parkRunnable(final String key, final Runnable runnable, final Map<String, Runnable> map, final Array<Runnable> runnables) {
        map.put(key, runnable);
        runnables.add(runnable);
    }

    /**
     * Removes a previously parked update runnable.
     *
     * @param key The key of the runnable to remove.
     */
    public void removeRunnable(final String key) {
        removeRunnable(key, parkedUpdateRunnablesMap, parkedUpdateRunnables);
        removeRunnable(key, parkedCameraRunnablesMap, parkedCameraRunnables);
    }

    private void removeRunnable(final String key, final Map<String, Runnable> map, final Array<Runnable> runnables) {
        if (map.containsKey(key)) {
            final Runnable r = map.get(key);
            if (r != null) {
                runnables.removeValue(r, true);
                map.remove(key);
            }
        }
    }

}
