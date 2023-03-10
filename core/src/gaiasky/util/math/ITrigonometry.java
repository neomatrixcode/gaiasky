/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.math;

/**
 * Trigonometry interface to enable multiple implementations
 */
public interface ITrigonometry {
    double sin(double angle);

    double asin(double angle);

    double cos(double angle);

    double acos(double angle);

    double tan(double angle);

    double atan(double angle);

    double atan2(double y, double x);

}
