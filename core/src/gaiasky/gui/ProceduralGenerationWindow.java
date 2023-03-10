/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.sudoplay.joise.module.ModuleBasisFunction.BasisType;
import com.sudoplay.joise.module.ModuleFractal.FractalType;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.scene.Mapper;
import gaiasky.scene.record.*;
import gaiasky.scene.view.FocusView;
import gaiasky.util.Constants;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import gaiasky.util.SysUtils;
import gaiasky.util.color.ColorUtils;
import gaiasky.util.i18n.I18n;
import gaiasky.util.scene2d.*;
import gaiasky.util.validator.LongValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProceduralGenerationWindow extends GenericDialog implements IObserver {
    private static final Log logger = Logger.getLogger(ProceduralGenerationWindow.class);
    // Selected tab persists across windows
    private static int lastTabSelected = 0;

    private final Entity target;
    private final FocusView view;
    private Random rand;
    private MaterialComponent initMtc, mtc;
    private CloudComponent initClc, clc;
    private AtmosphereComponent initAc, ac;
    private float fieldWidth, fieldWidthAll, textWidth;
    private boolean updateTabSelected = true;
    private OwnSliderPlus hueShift;
    private Texture currentLutTexture;
    private Cell<?> lutImageCell;
    private OwnTextButton genCloudsButton, genSurfaceButton;

    private int genCloudNum = 0, genSurfaceNum = 0;

    public ProceduralGenerationWindow(FocusView target, Stage stage, Skin skin) {
        super(I18n.msg("gui.procedural.title", target.getLocalizedName()), skin, stage);
        this.target = target.getEntity();
        this.view = new FocusView(target.getEntity());
        this.initMtc = Mapper.model.get(this.target).model.mtc;
        this.initClc = Mapper.cloud.get(this.target).cloud;
        this.initAc = Mapper.atmosphere.get(this.target).atmosphere;
        this.rand = new Random(1884L);
        this.setModal(false);

        EventManager.instance.subscribe(this, Event.PROCEDURAL_GENERATION_CLOUD_INFO, Event.PROCEDURAL_GENERATION_SURFACE_INFO);

        setAcceptText(I18n.msg("gui.close"));

        // Build UI
        buildSuper();

    }

    protected void rebuild() {
        this.content.clear();
        build();
    }

    @Override
    protected void build() {
        this.textWidth = 195f;
        this.fieldWidth = 500f;
        this.fieldWidthAll = 710f;
        float tabContentWidth = 400f;
        float tabWidth = 240f;

        // Create the tab buttons
        HorizontalGroup group = new HorizontalGroup();
        group.align(Align.left);

        final Button tabSurface = new OwnTextButton(I18n.msg("gui.procedural.surface"), skin, "toggle-big");
        tabSurface.pad(pad10);
        tabSurface.setWidth(tabWidth);
        final Button tabClouds = new OwnTextButton(I18n.msg("gui.procedural.cloud"), skin, "toggle-big");
        tabClouds.pad(pad10);
        tabClouds.setWidth(tabWidth);
        final Button tabAtmosphere = new OwnTextButton(I18n.msg("gui.procedural.atmosphere"), skin, "toggle-big");
        tabAtmosphere.pad(pad10);
        tabAtmosphere.setWidth(tabWidth);

        group.addActor(tabSurface);
        group.addActor(tabClouds);
        group.addActor(tabAtmosphere);

        content.add(group).left().row();

        // Create the tab content. Just using images here for simplicity.
        Stack tabContent = new Stack();

        // SURFACE
        final Table contentSurface = new Table(skin);
        contentSurface.setWidth(tabContentWidth);
        contentSurface.align(Align.top | Align.left);
        buildContentSurface(contentSurface);

        // CLOUDS
        final Table contentClouds = new Table(skin);
        contentClouds.setWidth(tabContentWidth);
        contentClouds.align(Align.top | Align.left);
        buildContentClouds(contentClouds);

        // ATMOSPHERE
        final Table contentAtmosphere = new Table(skin);
        contentAtmosphere.setWidth(tabContentWidth);
        contentAtmosphere.align(Align.top | Align.left);
        buildContentAtmosphere(contentAtmosphere);

        /* ADD ALL CONTENT */
        tabContent.addActor(contentSurface);
        tabContent.addActor(contentClouds);
        tabContent.addActor(contentAtmosphere);

        content.add(tabContent).padTop(pad34).padBottom(pad34).expand().fill().row();

        // Listen to changes in the tab button checked states
        // Set visibility of the tab content to match the checked state
        ChangeListener tabListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                contentSurface.setVisible(tabSurface.isChecked());
                contentClouds.setVisible(tabClouds.isChecked());
                contentAtmosphere.setVisible(tabAtmosphere.isChecked());
                if (updateTabSelected) {
                    if (tabSurface.isChecked())
                        lastTabSelected = 0;
                    else if (tabClouds.isChecked())
                        lastTabSelected = 1;
                    else if (tabAtmosphere.isChecked())
                        lastTabSelected = 2;
                }
            }
        };
        tabSurface.addListener(tabListener);
        tabClouds.addListener(tabListener);
        tabAtmosphere.addListener(tabListener);

        // Let only one tab button be checked at a time
        ButtonGroup<Button> tabs = new ButtonGroup<>();
        tabs.setMinCheckCount(1);
        tabs.setMaxCheckCount(1);
        updateTabSelected = false;
        tabs.add(tabSurface);
        tabs.add(tabClouds);
        tabs.add(tabAtmosphere);
        updateTabSelected = true;

        tabs.setChecked(((TextButton) tabs.getButtons().get(lastTabSelected)).getText().toString());

        // Randomize button
        OwnTextButton randomize = new OwnTextButton(I18n.msg("gui.procedural.randomize", I18n.msg("gui.procedural.all")), skin, "big");
        randomize.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                randomizeAll();
            }
        });
        randomize.pad(pad10, pad20, pad10, pad20);
        // Generate button
        OwnTextButton generate = new OwnTextButton(I18n.msg("gui.procedural.generate", I18n.msg("gui.procedural.all")), skin, "big");
        generate.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                generateAll();
            }
        });
        generate.pad(pad10, pad20, pad10, pad20);

        HorizontalGroup buttonGroup = new HorizontalGroup();
        buttonGroup.space(pad20);
        buttonGroup.addActor(randomize);
        buttonGroup.addActor(generate);

        content.add(buttonGroup).center().padBottom(pad34).row();

        // Save textures
        OwnCheckBox saveTextures = new OwnCheckBox(I18n.msg("gui.procedural.savetextures"), skin, pad10);
        saveTextures.setChecked(Settings.settings.program.saveProceduralTextures);
        saveTextures.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Settings.settings.program.saveProceduralTextures = saveTextures.isChecked();
            }
        });
        OwnImageButton saveTexturesTooltip = new OwnImageButton(skin, "tooltip");
        saveTexturesTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.savetextures", SysUtils.getProceduralPixmapDir().toString()), skin));
        HorizontalGroup saveTexturesGroup = new HorizontalGroup();
        saveTexturesGroup.space(pad10);
        saveTexturesGroup.addActor(saveTextures);
        saveTexturesGroup.addActor(saveTexturesTooltip);
        content.add(saveTexturesGroup).left();

    }

    private OwnTextButton addLocalButtons(Table content, String key, Function<Boolean, Boolean> randomizeFunc, Function<Boolean, Boolean> generateFunc) {
        String name = I18n.msg(key);
        // Randomize button
        OwnTextButton randomize = new OwnTextButton(I18n.msg("gui.procedural.randomize", name), skin);
        randomize.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                randomizeFunc.apply(true);
            }
        });
        randomize.pad(pad10, pad20, pad10, pad20);
        // Generate button
        OwnTextButton generate = new OwnTextButton(I18n.msg("gui.procedural.generate", name), skin);
        generate.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                generateFunc.apply(true);
            }
        });
        generate.pad(pad10, pad20, pad10, pad20);

        HorizontalGroup buttonGroup = new HorizontalGroup();
        buttonGroup.space(pad20);
        buttonGroup.addActor(randomize);
        buttonGroup.addActor(generate);

        content.add(buttonGroup).center().colspan(2).padBottom(pad18).row();
        content.add(new Separator(skin, "menu")).center().colspan(2).growX().padBottom(pad34);

        return generate;
    }

    private void addNoiseGroup(Table content, NoiseComponent nc, String key) {
        // Title
        content.add(new OwnLabel(I18n.msg(key), skin, "header")).colspan(2).left().padBottom(pad34).row();

        // Seed
        LongValidator lv = new LongValidator();
        OwnTextField seedField = new OwnTextField(Long.toString(nc.seed), skin);
        seedField.setWidth(fieldWidth);
        seedField.setValidator(lv);
        seedField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.seed = seedField.getLongValue(1L);
            }
        });

        OwnLabel seedLabel = new OwnLabel(I18n.msg("gui.procedural.seed"), skin);
        seedLabel.setWidth(textWidth);
        OwnImageButton seedTooltip = new OwnImageButton(skin, "tooltip");
        seedTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.seed"), skin));
        content.add(seedLabel).left().padBottom(pad18).padRight(pad18);
        content.add(seedField).padBottom(pad18).padRight(pad10);
        content.add(seedTooltip).padBottom(pad18).row();

        // Noise type
        OwnSelectBox<BasisType> type = new OwnSelectBox<>(skin);
        type.setItems(BasisType.values());
        type.setWidth(fieldWidth);
        type.setSelected(nc.type);
        type.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.type = type.getSelected();
            }
        });
        OwnLabel typeLabel = new OwnLabel(I18n.msg("gui.procedural.type"), skin);
        typeLabel.setWidth(textWidth);
        OwnImageButton typeTooltip = new OwnImageButton(skin, "tooltip");
        typeTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.type"), skin));
        content.add(typeLabel).left().padBottom(pad18).padRight(pad18);
        content.add(type).padBottom(pad18).padRight(pad10);
        content.add(typeTooltip).padBottom(pad18).row();

        // Fractal type
        OwnSelectBox<FractalType> fractalType = new OwnSelectBox<>(skin);
        fractalType.setItems(FractalType.values());
        fractalType.setWidth(fieldWidth);
        fractalType.setSelected(nc.fractalType);
        fractalType.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.fractalType = fractalType.getSelected();
            }
        });
        OwnLabel fractalLabel = new OwnLabel(I18n.msg("gui.procedural.fractaltype"), skin);
        fractalLabel.setWidth(textWidth);
        OwnImageButton fractalTooltip = new OwnImageButton(skin, "tooltip");
        fractalTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.fractaltype"), skin));
        content.add(fractalLabel).left().padBottom(pad18).padRight(pad18);
        content.add(fractalType).padBottom(pad18).padRight(pad10);
        content.add(fractalTooltip).padBottom(pad18).row();

        // Scale
        OwnSliderPlus scaleX = new OwnSliderPlus(I18n.msg("gui.procedural.scale", "[x]"), 0.01f, 10.0f, 0.01f, skin);
        scaleX.setWidth(fieldWidthAll / 3f - pad10 * 1.3f);
        scaleX.setValue((float) nc.scale[0]);
        scaleX.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.scale[0] = scaleX.getMappedValue();
            }
        });
        OwnSliderPlus scaleY = new OwnSliderPlus(I18n.msg("gui.procedural.scale", "[y]"), 0.01f, 10.0f, 0.01f, skin);
        scaleY.setWidth(fieldWidthAll / 3f - pad10 * 1.3f);
        scaleY.setValue((float) nc.scale[1]);
        scaleY.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.scale[1] = scaleY.getMappedValue();
            }
        });
        OwnSliderPlus scaleZ = new OwnSliderPlus(I18n.msg("gui.procedural.scale", "[z]"), 0.01f, 10.0f, 0.01f, skin);
        scaleZ.setWidth(fieldWidthAll / 3f - pad10 * 1.3f);
        scaleZ.setValue((float) nc.scale[2]);
        scaleZ.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.scale[2] = scaleZ.getMappedValue();
            }
        });

        HorizontalGroup scaleGroup = new HorizontalGroup();
        scaleGroup.space(pad10 * 2f);
        scaleGroup.addActor(scaleX);
        scaleGroup.addActor(scaleY);
        scaleGroup.addActor(scaleZ);
        OwnImageButton scaleTooltip = new OwnImageButton(skin, "tooltip");
        scaleTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.scale"), skin));
        content.add(scaleGroup).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(scaleTooltip).left().padBottom(pad18).row();

        // Frequency
        OwnSliderPlus frequency = new OwnSliderPlus(I18n.msg("gui.procedural.frequency"), 0.1f, 15.0f, 0.1f, skin);
        frequency.setWidth(fieldWidthAll);
        frequency.setValue((float) nc.frequency);
        frequency.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.frequency = frequency.getMappedValue();
            }
        });
        OwnImageButton frequencyTooltip = new OwnImageButton(skin, "tooltip");
        frequencyTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.frequency"), skin));
        content.add(frequency).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(frequencyTooltip).left().padBottom(pad18).row();

        // Lacunarity
        OwnSliderPlus lacunarity = new OwnSliderPlus(I18n.msg("gui.procedural.lacunarity"), 0.1f, 15.0f, 0.1f, skin);
        lacunarity.setWidth(fieldWidthAll);
        lacunarity.setValue((float) nc.lacunarity);
        lacunarity.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.lacunarity = lacunarity.getMappedValue();
            }
        });
        OwnImageButton lacunarityTooltip = new OwnImageButton(skin, "tooltip");
        lacunarityTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.lacunarity"), skin));
        content.add(lacunarity).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(lacunarityTooltip).left().padBottom(pad18).row();

        // Octaves
        OwnSliderPlus octaves = new OwnSliderPlus(I18n.msg("gui.procedural.octaves"), 1, 9, 1, skin);
        octaves.setWidth(fieldWidthAll);
        octaves.setValue(nc.octaves);
        octaves.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.octaves = (int) octaves.getMappedValue();
            }
        });
        OwnImageButton octavesTooltip = new OwnImageButton(skin, "tooltip");
        octavesTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.octaves"), skin));
        content.add(octaves).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(octavesTooltip).left().padBottom(pad18).row();

        // Range
        OwnSliderPlus rangeMin = new OwnSliderPlus(I18n.msg("gui.procedural.range", "[min]"), -2f, 0.0f, 0.1f, skin);
        rangeMin.setWidth(fieldWidthAll / 2f - pad10);
        rangeMin.setValue((float) nc.range[0]);
        rangeMin.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.range[0] = rangeMin.getMappedValue();
            }
        });
        OwnSliderPlus rangeMax = new OwnSliderPlus(I18n.msg("gui.procedural.range", "[max]"), 0.5f, 2.0f, 0.1f, skin);
        rangeMax.setWidth(fieldWidthAll / 2f - pad10);
        rangeMax.setValue((float) nc.range[1]);
        rangeMax.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.range[1] = rangeMax.getMappedValue();
            }
        });

        HorizontalGroup rangeGroup = new HorizontalGroup();
        rangeGroup.space(pad10 * 2f);
        rangeGroup.addActor(rangeMin);
        rangeGroup.addActor(rangeMax);
        OwnImageButton rangeTooltip = new OwnImageButton(skin, "tooltip");
        rangeTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.range"), skin));
        content.add(rangeGroup).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(rangeTooltip).left().padBottom(pad18).row();

        // Power
        OwnSliderPlus power = new OwnSliderPlus(I18n.msg("gui.procedural.power"), 0.1f, 20f, 0.1f, skin);
        power.setWidth(fieldWidthAll);
        power.setValue((float) nc.power);
        power.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nc.power = power.getMappedValue();
            }
        });
        OwnImageButton powerTooltip = new OwnImageButton(skin, "tooltip");
        powerTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.power"), skin));
        content.add(power).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(powerTooltip).left().padBottom(pad18).row();

    }

    private void updateLutImage(Array<String> luts) {
        if (lutImageCell != null) {
            lutImageCell.clearActor();
            Pixmap p = new Pixmap(Settings.settings.data.dataFileHandle(luts.get(luts.indexOf(mtc.biomeLUT, false))));
            int w = p.getWidth();
            int h = p.getHeight();
            if (hueShift != null) {
                float hue = hueShift.getMappedValue();
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        Color col = new Color(p.getPixel(x, y));
                        float[] rgb = new float[] { col.r, col.g, col.b, 1f };
                        if (hue != 0) {
                            // Shift hue of lookup table by an amount in degrees
                            float[] hsb = ColorUtils.rgbToHsb(rgb);
                            hsb[0] = ((hsb[0] * 360f + hue) % 360f) / 360f;
                            rgb = ColorUtils.hsbToRgb(hsb);
                        }
                        col.set(rgb[0], rgb[1], rgb[2], 1f);
                        p.drawPixel(x, y, Color.rgba8888(col));
                    }
                }
            }
            Texture newLutTexture = new Texture(p);
            Image img = new Image(newLutTexture);
            lutImageCell.setActor(img);
            if (currentLutTexture != null) {
                currentLutTexture.dispose();
            }
            currentLutTexture = newLutTexture;
            p.dispose();
        }
    }

    private void buildContentSurface(Table content) {
        ModelComponent mc = Mapper.model.get(target).model;
        if (mc != null) {
            mtc = new MaterialComponent();
            if (initMtc == null) {
                // Generate random material
                mtc.randomizeAll(rand.nextLong(), view.getSize());
            } else {
                // Copy existing
                mtc.copyFrom(initMtc);
            }
            // Title
            content.add(new OwnLabel(I18n.msg("gui.procedural.param.color"), skin, "header")).colspan(2).left().padBottom(pad34).row();

            // LUT
            Path dataPath = Settings.settings.data.dataPath("tex/base");
            Array<String> luts = new Array<>();
            try {
                java.util.List<Path> l = Files.list(dataPath).filter(f -> f.toString().endsWith("-lut.png")).collect(Collectors.toList());
                for (Path p : l) {
                    String name = p.toString();
                    luts.add("data" + name.substring(name.indexOf("/tex/base/")));
                }
            } catch (Exception ignored) {
            }
            if (luts.isEmpty()) {
                luts.add(Constants.DATA_LOCATION_TOKEN + "tex/base/biome-lut.png");
                luts.add(Constants.DATA_LOCATION_TOKEN + "tex/base/biome-smooth-lut.png");
            }
            OwnSelectBox<String> lutsBox = new OwnSelectBox<>(skin);
            lutsBox.setItems(luts);
            lutsBox.setWidth(fieldWidth);
            lutsBox.setSelected(mtc.biomeLUT);
            lutsBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    mtc.biomeLUT = lutsBox.getSelected();
                    updateLutImage(luts);
                }
            });

            OwnLabel lutLabel = new OwnLabel(I18n.msg("gui.procedural.lut"), skin);
            lutLabel.setWidth(textWidth);
            OwnImageButton lutTooltip = new OwnImageButton(skin, "tooltip");
            lutTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.lut"), skin));
            content.add(lutLabel).left().padBottom(pad18).padRight(pad18);
            content.add(lutsBox).left().padBottom(pad18).padRight(pad10);
            content.add(lutTooltip).left().padBottom(pad18).row();
            lutImageCell = content.add();
            lutImageCell.colspan(3).padBottom(pad18).row();

            // Hue shift
            hueShift = new OwnSliderPlus(I18n.msg("gui.procedural.hueshift"), 0.0f, 360.0f, 0.1f, skin);
            hueShift.setWidth(fieldWidthAll);
            hueShift.setValueSuffix("??");
            hueShift.setValue(mtc.biomeHueShift);
            hueShift.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    mtc.biomeHueShift = hueShift.getMappedValue();
                    updateLutImage(luts);
                }
            });
            OwnImageButton hueShiftTooltip = new OwnImageButton(skin, "tooltip");
            hueShiftTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.hueshift"), skin));
            content.add(hueShift).colspan(2).left().padBottom(pad34).padRight(pad10);
            content.add(hueShiftTooltip).left().padBottom(pad34).row();

            // Initial update
            updateLutImage(luts);

            // Noise
            addNoiseGroup(content, mtc.nc, "gui.procedural.param.elev");

            // Height scale
            OwnSliderPlus heightScale = new OwnSliderPlus(I18n.msg("gui.procedural.heightscale"), 1.0f, 80.0f, 0.1f, skin);
            heightScale.setWidth(fieldWidthAll);
            heightScale.setValueSuffix(" km");
            heightScale.setValue((float) (mtc.heightScale * Constants.U_TO_KM));
            heightScale.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    mtc.heightScale = (float) (heightScale.getMappedValue() * Constants.KM_TO_U);
                }
            });
            OwnImageButton heightScaleTooltip = new OwnImageButton(skin, "tooltip");
            heightScaleTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.heightscale"), skin));
            content.add(heightScale).colspan(2).left().padBottom(pad34).padRight(pad10);
            content.add(heightScaleTooltip).left().padBottom(pad34).row();

            // Add button group
            genSurfaceButton = addLocalButtons(content, "gui.procedural.surface", this::randomizeSurface, this::generateSurface);

        } else {
            // Error!
            OwnLabel l = new OwnLabel(I18n.msg("gui.procedural.nomodel", view.getName()), skin);
            content.add(l).pad(pad34).center();
        }
    }

    private void buildContentClouds(Table content) {
        clc = new CloudComponent();
        if (initClc == null) {
            // Generate random
            clc.randomizeAll(rand.nextLong(), view.getSize());
        } else {
            // Copy existing
            clc.copyFrom(initClc);
            clc.setDiffuse("generate");
        }
        // Fog color
        ColorPicker cloudColor = new ColorPicker(new float[] { clc.color[0], clc.color[1], clc.color[2], clc.color[3] }, stage, skin);
        cloudColor.setSize(128f, 128f);
        cloudColor.setNewColorRunnable(() -> {
            float[] col = cloudColor.getPickedColor();
            clc.color[0] = col[0];
            clc.color[1] = col[1];
            clc.color[2] = col[2];
            clc.color[3] = col[3];
        });
        OwnLabel cloudColorLabel = new OwnLabel(I18n.msg("gui.procedural.cloudcolor"), skin);
        cloudColorLabel.setWidth(textWidth);
        OwnImageButton cloudColorTooltip = new OwnImageButton(skin, "tooltip");
        cloudColorTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.cloudcolor"), skin));
        content.add(cloudColorLabel).left().padRight(pad18).padBottom(pad18);
        content.add(cloudColor).left().expandX().padBottom(pad18).padRight(pad10);
        content.add(cloudColorTooltip).left().padBottom(pad18).row();

        // Noise
        addNoiseGroup(content, clc.nc, "gui.procedural.param.cloud");

        // Add button group
        genCloudsButton = addLocalButtons(content, "gui.procedural.cloud", this::randomizeClouds, this::generateClouds);
    }

    private void buildContentAtmosphere(Table content) {
        ac = new AtmosphereComponent();
        if (initAc == null) {
            // Generate random
            ac.randomizeAll(rand.nextLong(), view.getSize());
        } else {
            // Copy existing
            ac.copyFrom(initAc);
        }
        // Title
        content.add(new OwnLabel(I18n.msg("gui.procedural.param.atm"), skin, "header")).colspan(2).left().padBottom(pad34).row();

        // Wavelengths
        OwnSliderPlus wavelength0 = new OwnSliderPlus(I18n.msg("gui.procedural.wavelength", "[0]"), 0.4f, 1.0f, 0.01f, skin);
        wavelength0.setWidth(fieldWidthAll / 3f - pad10 * 1.3f);
        wavelength0.setValue((float) ac.wavelengths[0]);
        wavelength0.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.wavelengths[0] = wavelength0.getMappedValue();
            }
        });
        OwnSliderPlus wavelength1 = new OwnSliderPlus(I18n.msg("gui.procedural.wavelength", "[1]"), 0.4f, 1.0f, 0.01f, skin);
        wavelength1.setWidth(fieldWidthAll / 3f - pad10 * 1.3f);
        wavelength1.setValue((float) ac.wavelengths[1]);
        wavelength1.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.wavelengths[1] = wavelength1.getMappedValue();
            }
        });
        OwnSliderPlus wavelength2 = new OwnSliderPlus(I18n.msg("gui.procedural.wavelength", "[2]"), 0.4f, 1.0f, 0.01f, skin);
        wavelength2.setWidth(fieldWidthAll / 3f - pad10 * 1.3f);
        wavelength2.setValue((float) ac.wavelengths[1]);
        wavelength2.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.wavelengths[2] = wavelength2.getMappedValue();
            }
        });

        HorizontalGroup wavelengthGroup = new HorizontalGroup();
        wavelengthGroup.space(pad10 * 2f);
        wavelengthGroup.addActor(wavelength0);
        wavelengthGroup.addActor(wavelength1);
        wavelengthGroup.addActor(wavelength2);
        OwnImageButton wavelengthTooltip = new OwnImageButton(skin, "tooltip");
        wavelengthTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.wavelength"), skin));
        content.add(wavelengthGroup).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(wavelengthTooltip).left().padBottom(pad18).row();

        // eSun
        OwnSliderPlus eSun = new OwnSliderPlus(I18n.msg("gui.procedural.esun"), -1.0f, 25.0f, 0.1f, skin);
        eSun.setWidth(fieldWidthAll);
        eSun.setValue(ac.m_eSun);
        eSun.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.m_eSun = eSun.getMappedValue();
            }
        });
        OwnImageButton esunTooltip = new OwnImageButton(skin, "tooltip");
        esunTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.esun"), skin));
        content.add(eSun).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(esunTooltip).left().padBottom(pad18).row();

        DecimalFormat nf = new DecimalFormat("#0.0000##");
        // Kr
        OwnSliderPlus kr = new OwnSliderPlus(I18n.msg("gui.procedural.kr"), 0.0f, 0.01f, 0.0001f, skin);
        kr.setWidth(fieldWidthAll);
        kr.setNumberFormatter(nf);
        kr.setValue(ac.m_Kr);
        kr.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.m_Kr = kr.getMappedValue();
            }
        });
        OwnImageButton krTooltip = new OwnImageButton(skin, "tooltip");
        krTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.kr"), skin));
        content.add(kr).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(krTooltip).left().padBottom(pad18).row();

        // Kr
        OwnSliderPlus km = new OwnSliderPlus(I18n.msg("gui.procedural.km"), 0.0f, 0.01f, 0.0001f, skin);
        km.setWidth(fieldWidthAll);
        km.setNumberFormatter(nf);
        km.setValue(ac.m_Km);
        km.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.m_Km = km.getMappedValue();
            }
        });
        OwnImageButton kmTooltip = new OwnImageButton(skin, "tooltip");
        kmTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.km"), skin));
        content.add(km).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(kmTooltip).left().padBottom(pad18).row();

        // Fog density
        OwnSliderPlus fogDensity = new OwnSliderPlus(I18n.msg("gui.procedural.fogdensity"), 0.5f, 10.0f, 0.1f, skin);
        fogDensity.setWidth(fieldWidthAll);
        fogDensity.setValue(ac.fogDensity);
        fogDensity.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.fogDensity = fogDensity.getMappedValue();
            }
        });
        OwnImageButton fogDensityTooltip = new OwnImageButton(skin, "tooltip");
        fogDensityTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.fogdensity"), skin));
        content.add(fogDensity).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(fogDensityTooltip).left().padBottom(pad18).row();

        // Num samples
        OwnSliderPlus samples = new OwnSliderPlus(I18n.msg("gui.procedural.samples"), 2, 50, 1, skin);
        samples.setWidth(fieldWidthAll);
        samples.setValue(ac.samples);
        samples.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ac.samples = (int) samples.getValue();
            }
        });
        OwnImageButton samplesTooltip = new OwnImageButton(skin, "tooltip");
        samplesTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.samples"), skin));
        content.add(samples).colspan(2).left().padBottom(pad18).padRight(pad10);
        content.add(samplesTooltip).left().padBottom(pad18).row();

        // Fog color
        ColorPicker fogColor = new ColorPicker(new float[] { ac.fogColor.x, ac.fogColor.y, ac.fogColor.z, 1f }, stage, skin);
        fogColor.setSize(128f, 128f);
        fogColor.setNewColorRunnable(() -> {
            float[] col = fogColor.getPickedColor();
            ac.fogColor.x = col[0];
            ac.fogColor.y = col[1];
            ac.fogColor.z = col[2];
        });
        OwnLabel fogColorLabel = new OwnLabel(I18n.msg("gui.procedural.fogcolor"), skin);
        fogColorLabel.setWidth(textWidth);
        OwnImageButton fogColorTooltip = new OwnImageButton(skin, "tooltip");
        fogColorTooltip.addListener(new OwnTextTooltip(I18n.msg("gui.procedural.info.fogcolor"), skin));
        content.add(fogColorLabel).left().padRight(pad18).padBottom(pad18);
        content.add(fogColor).left().expandX().padBottom(pad18).padRight(pad10);
        content.add(fogColorTooltip).left().padBottom(pad18).row();

        // Add button group
        addLocalButtons(content, "gui.procedural.atmosphere", this::randomizeAtmosphere, this::generateAtmosphere);
    }

    protected Boolean randomizeSurface(Boolean rebuild) {
        this.initMtc = new MaterialComponent();
        this.initMtc.randomizeAll(rand.nextLong(), view.getSize());

        if (rebuild) {
            // Others are the same
            this.initClc = this.clc;
            this.initAc = this.ac;

            rebuild();
        }

        return true;
    }

    protected Boolean randomizeClouds(Boolean rebuild) {
        this.initClc = new CloudComponent();
        this.initClc.randomizeAll(rand.nextLong(), view.getSize());

        if (rebuild) {
            // Others are the same
            this.initMtc = this.mtc;
            this.initAc = this.ac;

            rebuild();
        }

        return true;
    }

    protected Boolean randomizeAtmosphere(Boolean rebuild) {
        this.initAc = new AtmosphereComponent();
        this.initAc.randomizeAll(rand.nextLong(), view.getSize());

        if (rebuild) {
            // Others are the same
            this.initMtc = this.mtc;
            this.initClc = this.clc;

            rebuild();
        }

        return true;
    }

    protected void randomizeAll() {
        randomizeSurface(false);
        randomizeClouds(false);
        randomizeAtmosphere(false);
        rebuild();
    }

    protected Boolean generateSurface(Boolean ignored) {
        GaiaSky.postRunnable(this::generateSurfaceDirect);
        return ignored;
    }

    protected void generateSurfaceDirect() {
        if (genSurfaceNum == 0) {
            var model = Mapper.model.get(target);
            var materialComponent = model.model.mtc;
            if (materialComponent != null) {
                materialComponent.disposeTextures(GaiaSky.instance.assetManager);
            }
            mtc.initialize(view.getName());
            model.model.setMaterial(mtc);
        } else {
            logger.info(I18n.msg("gui.procedural.error.gen", I18n.msg("gui.procedural.surface")));
        }
    }

    protected Boolean generateClouds(Boolean ignored) {
        GaiaSky.postRunnable(this::generateCloudsDirect);
        return ignored;
    }

    protected void generateCloudsDirect() {
        if (genCloudNum == 0) {
            var cloud = Mapper.cloud.get(target);
            CloudComponent cloudComponent = cloud.cloud;
            if (cloudComponent != null) {
                cloudComponent.disposeTextures(GaiaSky.instance.assetManager);
            }
            clc.initialize(view.getName(), false);
            cloud.cloud = clc;
            cloud.cloud.doneLoading(GaiaSky.instance.assetManager);
        } else {
            logger.info(I18n.msg("gui.procedural.error.gen", I18n.msg("gui.procedural.cloud")));
        }
    }

    protected Boolean generateAtmosphere(Boolean ignored) {
        GaiaSky.postRunnable(this::generateAtmosphereDirect);
        return ignored;
    }

    protected void generateAtmosphereDirect() {
        var atm = Mapper.atmosphere.get(target);
        var model = Mapper.model.get(target);
        atm.atmosphere = ac;
        atm.atmosphere.doneLoading(model.model.instance.materials.first(), (float) view.getSize());
    }

    protected void generateAll() {
        GaiaSky.postRunnable(() -> {
            generateSurfaceDirect();
            generateCloudsDirect();
            generateAtmosphereDirect();
        });
    }

    @Override
    protected boolean accept() {
        return true;
    }

    @Override
    protected void cancel() {
    }

    /**
     * Sets the enabled property on the given components
     */
    protected void enableComponents(boolean enabled, Disableable... components) {
        for (Disableable c : components) {
            if (c != null)
                c.setDisabled(!enabled);
        }
    }

    @Override
    public void dispose() {

    }

    public void setKeyboardFocus() {
        stage.setKeyboardFocus(null);
    }

    private void updateButtonStatus() {
        genCloudsButton.setDisabled(genCloudNum > 0);
        genSurfaceButton.setDisabled(genSurfaceNum > 0);
    }

    @Override
    public void notify(Event event, Object source, Object... data) {
        boolean status = (Boolean) data[0];
        switch (event) {
        case PROCEDURAL_GENERATION_CLOUD_INFO -> {
            if (status) {
                genCloudNum++;
            } else {
                genCloudNum = Math.max(genCloudNum - 1, 0);
            }
            updateButtonStatus();
        }
        case PROCEDURAL_GENERATION_SURFACE_INFO -> {
            if (status) {
                genSurfaceNum++;
            } else {
                genSurfaceNum = Math.max(genSurfaceNum - 1, 0);
            }
            updateButtonStatus();
        }
        }
    }
}
