/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.record;

import gaiasky.util.Constants;
import gaiasky.util.Nature;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.math.MathUtilsDouble;
import gaiasky.util.math.Vector3d;

import java.time.Instant;
import java.util.Objects;

public class OrbitComponent {

    /** Source file **/
    public String source;
    /** Orbital period in days **/
    public double period;
    /** Base epoch in julian days **/
    public double epoch;
    /** Semi major axis of the ellipse, a in Km. **/
    public double semimajoraxis;
    /** Eccentricity of the ellipse, in degrees. **/
    public double e;
    /** Inclination, angle between the reference plane and the orbital plane, in degrees. **/
    public double i;
    /** Longitude of the ascending node in degrees. **/
    public double ascendingnode;
    /** Argument of perihelion in degrees. **/
    public double argofpericenter;
    /** Mean anomaly at epoch, in degrees. **/
    public double meananomaly;
    /** G*M of central body (gravitational constant). Defaults to the Sun's **/
    public double mu = 1.32712440041e20;

    public OrbitComponent() {

    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setPeriod(Double period) {
        this.period = period;
    }

    public void setEpoch(Double epoch) {
        this.epoch = epoch;
    }

    public void setSemimajoraxis(Double semimajoraxis) {
        this.semimajoraxis = semimajoraxis;
    }

    public void setEccentricity(Double e) {
        this.e = e;
    }

    public void setInclination(Double i) {
        this.i = i;
    }

    public void setAscendingnode(Double ascendingnode) {
        this.ascendingnode = ascendingnode;
    }

    public void setArgofpericenter(Double argofpericenter) {
        this.argofpericenter = argofpericenter;
    }

    public void setMeananomaly(Double meanAnomaly) {
        this.meananomaly = meanAnomaly;
    }

    /**
     * This method automatically computes the standard gravitational
     * parameter (mu) of the orbit if the period and the semi-major axis
     * are set as mu=4 pi^2 a^3 / T^2.
     */
    public void computeMu() {
        if (period > 0 && semimajoraxis > 0) {
            double a = semimajoraxis * Nature.KM_TO_M; // km to m
            // Compute mu from period and semi-major axis
            double T = period * Nature.D_TO_S; // d to s
            this.mu = (4.0 * Math.PI * Math.PI * a * a * a) / (T * T);
        }
    }

    public void setMu(Double mu) {
        this.mu = mu;
    }

    public void loadDataPoint(Vector3d out, Instant t) {
        double tjd = AstroUtils.getJulianDate(t);
        loadDataPoint(out, tjd - epoch);
    }

    // See https://downloads.rene-schwarz.com/download/M001-Keplerian_Orbit_Elements_to_Cartesian_State_Vectors.pdf
    public void loadDataPoint(Vector3d out, double dtDays) {
        computeMu();
        double a = semimajoraxis * Nature.KM_TO_M; // km to m
        double M0 = meananomaly * MathUtilsDouble.degRad;
        double omega_lan = ascendingnode * MathUtilsDouble.degRad;
        double omega_ap = argofpericenter * MathUtilsDouble.degRad;
        double ic = i * MathUtilsDouble.degRad;

        // 1
        double dt = dtDays * Nature.D_TO_S;
        double M = M0 + dt * Math.sqrt(mu / Math.pow(a, 3d));

        // 2
        double E = M;
        for (int j = 0; j < 2; j++) {
            E = E - ((E - e * Math.sin(E) - M) / (1 - e * Math.cos(E)));
        }
        double E_t = E;

        // 3
        double nu_t = 2d * Math.atan2(Math.sqrt(1.0 + e) * Math.sin(E_t / 2.0), Math.sqrt(1.0 - e) * Math.cos(E_t / 2.0));

        // 4
        double rc_t = a * (1.0 - e * Math.cos(E_t));

        // 5
        double ox = rc_t * Math.cos(nu_t);
        double oy = rc_t * Math.sin(nu_t);

        // 6
        double sinomega = Math.sin(omega_ap);
        double cosomega = Math.cos(omega_ap);
        double sinOMEGA = Math.sin(omega_lan);
        double cosOMEGA = Math.cos(omega_lan);
        double cosi = Math.cos(ic);
        double sini = Math.sin(ic);

        double x = ox * (cosomega * cosOMEGA - sinomega * cosi * sinOMEGA) - oy * (sinomega * cosOMEGA + cosomega * cosi * sinOMEGA);
        double y = ox * (cosomega * sinOMEGA + sinomega * cosi * cosOMEGA) + oy * (cosomega * cosi * cosOMEGA - sinomega * sinOMEGA);
        double z = ox * (sinomega * sini) + oy * (cosomega * sini);

        // 7
        x *= Constants.M_TO_U;
        y *= Constants.M_TO_U;
        z *= Constants.M_TO_U;

        out.set(y, z, x);
    }

    @Override
    public String toString() {
        String desc;
        desc = Objects.requireNonNullElseGet(source, () -> "{epoch: " + epoch + ", period: " + period + ", e: " + e + ", i: " + i + ", sma: " + semimajoraxis + "}");
        return desc;
    }

}
