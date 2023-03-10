/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.record;

import gaiasky.scene.api.IUpdatable;
import gaiasky.util.Nature;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.time.ITimeFrameProvider;

/**
 * Provides the information for the rotation of this body
 */
public class RotationComponent implements IUpdatable<RotationComponent> {
    /** Angular velocity [deg/hour] around the rotation axis. **/
    public double angularVelocity;
    /** Current angle with respect to the rotationAxis in degrees. **/
    public double angle;

    /** The rotation period in hours. **/
    public double period;
    /** Angle between equatorial plane and orbital plane in degrees. **/
    public double axialTilt;
    /** Angle between orbital plane and the ecliptic in degrees. **/
    public double inclination;
    /** The ascending node in degrees, should not be used, as it obviously causes the body to wobble. **/
    public double ascendingNode;
    /** The meridian (hour) angle at the epoch J2000.0, in degrees **/
    public double meridianAngle;

    public RotationComponent() {
        this.angle = 0;
        this.angularVelocity = 0;
    }

    /**
     * Sets the rotation period.
     *
     * @param rotationPeriod The rotation period in hours.
     */
    public void setPeriod(Double rotationPeriod) {
        this.period = rotationPeriod;
        angularVelocity = 360.0 / rotationPeriod;
    }

    public void update(ITimeFrameProvider time) {
        long t = time.getTime().toEpochMilli() - AstroUtils.J2000_MS;
        angle = (meridianAngle + angularVelocity * t * Nature.MS_TO_H) % 360.0;
    }

    /**
     * Sets the axial tilt, the angle between the equatorial plane and the
     * orbital plane.
     *
     * @param f Angle in deg.
     */
    public void setAxialtilt(Double f) {
        this.axialTilt = f;
    }

    public void setAngle(Double angle) {
        this.angle = angle;
    }

    /**
     * Sets the inclination, the angle between the orbital plane and the
     * reference plane
     *
     * @param i Inclination in deg.
     */
    public void setInclination(Double i) {
        inclination = i;
    }

    /**
     * Sets the inclination, the angle between the orbital plane and the
     * reference plane
     *
     * @param i Inclination in deg.
     */
    public void setInclination(Long i) {
        inclination = i;
    }

    /**
     * Sets the ascending node.
     *
     * @param an Angle in deg.
     */
    public void setAscendingnode(Double an) {
        this.ascendingNode = an;
    }

    /**
     * Sets the meridian angle.
     *
     * @param ma Angle in deg.
     */
    public void setMeridianangle(Double ma) {
        this.meridianAngle = ma;
    }

    @Override
    public String toString() {
        return "{" + "angVel=" + angularVelocity + ", angle=" + angle + ", period=" + period + ", axialTilt=" + axialTilt + ", inclination=" + inclination + ", ascendingNode=" + ascendingNode + ", meridianAngle=" + meridianAngle + '}';
    }

    public void copyFrom(RotationComponent other) {
        if (other.period > 0) {
            this.period = other.period;
        }
        if (other.angularVelocity > 0) {
            this.angularVelocity = other.angularVelocity;
        }
        if (other.axialTilt > 0) {
            this.axialTilt = other.axialTilt;
        }
        if (other.inclination > 0) {
            this.inclination = other.inclination;
        }
        if (other.ascendingNode > 0) {
            this.ascendingNode = other.ascendingNode;
        }
        if (other.meridianAngle > 0) {
            this.meridianAngle = other.meridianAngle;
        }
    }

    @Override
    public void updateWith(RotationComponent object) {
        copyFrom(object);
    }
}
