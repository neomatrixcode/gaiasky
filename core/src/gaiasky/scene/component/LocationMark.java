package gaiasky.scene.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class LocationMark implements Component {
    public static final float LOWER_LIMIT = 3e-5f;
    public static final float UPPER_LIMIT = 3e-3f;

    /**
     * The display name
     **/
    public String displayName;

    /**
     * Longitude and latitude
     **/
    public Vector2 location;
    public Vector3 location3d;
    /**
     * This controls the distance from the center in case of non-spherical
     * objects
     **/
    public float distFactor = 1f;

    // Size in Km
    public float sizeKm;

    public void setLocation(double[] pos) {
        this.location = new Vector2((float) pos[0], (float) pos[1]);
    }

    public void setDistFactor(Double distFactor) {
        this.distFactor = distFactor.floatValue();
    }
}
