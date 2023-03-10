package gaiasky.scene.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import gaiasky.scene.record.ITransform;
import gaiasky.scene.record.RotateTransform;
import gaiasky.scene.record.ScaleTransform;
import gaiasky.scene.record.TranslateTransform;
import gaiasky.util.Constants;
import gaiasky.util.math.Matrix4d;

/**
 * Provides an arbitrary number of affine transformations (rotate, scale, translate) to be applied
 * to matrices.
 */
public class AffineTransformations implements Component {

    /** Affine transformations, applied each cycle **/
    public Array<ITransform> transformations;

    public void setTransformations(Object[] transformations) {
        initArray();
        for (Object transformation : transformations) {
            this.transformations.add((ITransform) transformation);
        }
    }

    private void initArray() {
        if (this.transformations == null) {
            this.transformations = new Array<>(3);
        }
    }

    public void setTranslate(double[] translation) {
        initArray();
        TranslateTransform tt = new TranslateTransform();
        tt.setVector(translation);
        this.transformations.add(tt);
    }

    public void setTranslatePc(double[] translation) {
        double[] iu = new double[3];
        iu[0] = translation[0] * Constants.PC_TO_U;
        iu[1] = translation[1] * Constants.PC_TO_U;
        iu[2] = translation[2] * Constants.PC_TO_U;
        setTranslate(iu);
    }

    public void setRotate(double[] axisDegrees) {
        initArray();
        RotateTransform rt = new RotateTransform();
        rt.setAxis(new double[] { axisDegrees[0], axisDegrees[1], axisDegrees[2] });
        rt.setAngle(axisDegrees[3]);
        this.transformations.add(rt);
    }

    public void setScale(double[] sc) {
        initArray();
        ScaleTransform st = new ScaleTransform();
        st.setScale(sc);
        this.transformations.add(st);
    }

    public Matrix4 apply(Matrix4 mat) {
        if (transformations != null) {
            for (ITransform tr : transformations) {
                tr.apply(mat);
            }
        }
        return mat;
    }

    public Matrix4d apply(Matrix4d mat) {
        if (transformations != null) {
            for (ITransform tr : transformations) {
                tr.apply(mat);
            }
        }
        return mat;
    }

    public ScaleTransform getScaleTransform() {
        if (this.transformations != null) {
            for (ITransform t : transformations) {
                if (t instanceof ScaleTransform) {
                    return (ScaleTransform) t;
                }
            }
        }
        return null;
    }

    public RotateTransform getRotateTransform() {
        if (this.transformations != null) {
            for (ITransform t : transformations) {
                if (t instanceof RotateTransform) {
                    return (RotateTransform) t;
                }
            }
        }
        return null;
    }

    public TranslateTransform getTranslateTransform() {
        if (this.transformations != null) {
            for (ITransform t : transformations) {
                if (t instanceof TranslateTransform) {
                    return (TranslateTransform) t;
                }
            }
        }
        return null;
    }
}
