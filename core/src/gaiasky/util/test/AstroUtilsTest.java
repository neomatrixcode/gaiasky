/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.test;

import gaiasky.util.coord.AstroUtils;
import gaiasky.util.math.MathManager;
import gaiasky.util.math.Vector3d;

/**
 * Implements the same test as in Jean Meeus' Astronomical Algorithms page 343.
 * Correct results are:
 * <p>
 * lambda[deg] : 133.16265398515185
 * beta[deg]   : 356.77087358075795 (3.229126419)
 * dist[km]    : 368409.6848161269
 */
public class AstroUtilsTest {
    public static void main(String[] args) {
        MathManager.initialize(true);
        Vector3d coord = new Vector3d();
        AstroUtils.moonEclipticCoordinates(2448724.5, coord);

        System.out.println("lambda[deg] : " + Math.toDegrees(coord.x));
        System.out.println("beta[deg]   : " + Math.toDegrees(coord.y));
        System.out.println("dist[km]    : " + coord.z);

        System.out.println("J2010: " + AstroUtils.JD_J2010);
    }
}
