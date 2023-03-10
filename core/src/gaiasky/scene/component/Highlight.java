package gaiasky.scene.component;

import com.badlogic.ashley.core.Component;
import gaiasky.util.filter.attrib.IAttribute;

public class Highlight implements Component {
    /**
     * Is it highlighted?
     */
    public boolean highlighted = false;
    // Plain color for highlighting
    public boolean hlplain = false;
    // Highlight color
    public float[] hlc = new float[4];
    // Highlight all visible
    public boolean hlallvisible = true;
    // Highlight colormap index
    public int hlcmi;
    // Highlight colormap attribute
    public IAttribute hlcma;
    // Highlight colormap min
    public double hlcmmin;
    // Highlight colormap max
    public double hlcmmax;
    // Point size scaling
    public float pointscaling = 1;

    public boolean isHighlighted() {
        return highlighted;
    }

    public boolean isHlplain() {
        return hlplain;
    }

    public int getHlcmi() {
        return hlcmi;
    }

    public IAttribute getHlcma() {
        return hlcma;
    }

    public double getHlcmmin() {
        return hlcmmin;
    }

    public double getHlcmmax() {
        return hlcmmax;
    }

    public boolean isHlAllVisible() {
        return hlallvisible;
    }
}
