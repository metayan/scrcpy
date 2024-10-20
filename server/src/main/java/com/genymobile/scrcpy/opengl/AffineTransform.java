package com.genymobile.scrcpy.opengl;

import com.genymobile.scrcpy.device.Size;

/**
 * Represents a 2D affine transform (a 3x3 matrix):
 *
 * <pre>
 *     / a c e \
 *     | b d f |
 *     \ 0 0 1 /
 * </pre>
 * <p>
 * Or, a 4x4 matrix if we add a z axis:
 *
 * <pre>
 *     / a c 0 e \
 *     | b d 0 f |
 *     | 0 0 1 0 |
 *     \ 0 0 0 1 /
 * </pre>
 */
public class AffineTransform {

    private final double a, b, c, d, e, f;

    public static final AffineTransform IDENTITY = new AffineTransform(1, 0, 0, 1, 0, 0);

    public AffineTransform(double a, double b, double c, double d, double e, double f) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    @Override
    public String toString() {
        return "[" + a + ", " + c + ", " + e + "; " + b + ", " + d + ", " + f + "]";
    }

    public AffineTransform apply(AffineTransform tr) {
        double aa = tr.a * this.a + tr.c * this.b;
        double bb = tr.b * this.a + tr.d * this.b;
        double cc = tr.a * this.c + tr.c * this.d;
        double dd = tr.b * this.c + tr.d * this.d;
        double ee = tr.a * this.e + tr.c * this.f + tr.e;
        double ff = tr.b * this.e + tr.d * this.f + tr.f;
        return new AffineTransform(aa, bb, cc, dd, ee, ff);
    }

    public AffineTransform invert() {
        // The 3x3 matrix M can be decomposed into M = M1 * M2:
        //         M1          M2
        //      / 1 0 e \   / a c 0 \
        //      | 0 1 f | * | b d 0 |
        //      \ 0 0 1 /   \ 0 0 1 /
        //
        // The inverse of an invertible 2x2 matrix is given by this formula:
        //
        //      / A B \⁻¹     1   /  D -B \
        //      \ C D /   = ----- \ -C  A /
        //                  AD-BC
        //
        // Let B=c and C=b (to apply the general formula with the same letters).
        //
        //     M⁻¹ = (M1 * M2)⁻¹ = M2⁻¹ * M1⁻¹
        //
        //                  M2⁻¹              M1⁻¹
        //           /----------------\
        //             1   /  d -B  0 \   / 1  0 -e \
        //         = ----- | -C  a  0 | * | 0  1 -f |
        //           ad-BC \  0  0  1 /   \ 0  0  1 /
        //
        // With the original letters:
        //
        //             1   /  d -c  0 \   / 1  0 -e \
        //     M⁻¹ = ----- | -b  a  0 | * | 0  1 -f |
        //           ad-cb \  0  0  1 /   \ 0  0  1 /
        //
        //             1   /  d -c  cf-de \
        //         = ----- | -b  a  be-af |
        //           ad-cb \  0  0    1   /
        double det = a * d - c * b;
        if (det == 0) {
            // Not invertible
            return null;
        }

        double aa = d / det;
        double bb = -b / det;
        double cc = -c / det;
        double dd = a / det;
        double ee = (c * f - d * e) / det;
        double ff = (b * e - a * f) / det;

        return new AffineTransform(aa, bb, cc, dd, ee, ff);
    }

    /**
     * Apply the transform from the center (0.5, 0.5).
     *
     * @return the translated transform.
     */
    public AffineTransform fromCenter() {
        return translate(-0.5, -0.5).apply(this).apply(translate(0.5, 0.5));
    }

    public AffineTransform withAspectRatio(double ar) {
        return scale(ar, 1).apply(this).apply(scale(1 / ar, 1));
    }

    public AffineTransform withAspectRatio(Size size) {
        double ar = (double) size.getWidth() / size.getHeight();
        return withAspectRatio(ar);
    }

    public static AffineTransform translate(double x, double y) {
        return new AffineTransform(1, 0, 0, 1, x, y);
    }

    public static AffineTransform scale(double x, double y) {
        return new AffineTransform(x, 0, 0, y, 0, 0);
    }

    /**
     * Reframe ("crop") a rectangle.
     * <p/>
     * <code>(x, y)</code> is the bottom-left corner, <code>(w, h)</code> is the size of the rectangle.
     *
     * @param x horizontal coordinate (increasing to the right)
     * @param y vertical coordinate (increasing upwards)
     * @param w width
     * @param h height
     * @return the associated transform
     */
    public static AffineTransform reframe(double x, double y, double w, double h) {
        if (w == 0 || h == 0) {
            throw new IllegalArgumentException("Cannot reframe to an empty area: " + w + "x" + h);
        }
        return translate(-x, -y).apply(scale(1 / w, 1 / h));
    }

    public static AffineTransform orient(int rotation) {
        switch (rotation) {
            case 0:
                return IDENTITY;
            case 1:
                // 90° counter-clockwise
                return new AffineTransform(0, 1, -1, 0, 0, 0);
            case 2:
                // 180°
                return new AffineTransform(-1, 0, 0, -1, 0, 0);
            case 3:
                // 90° clockwise
                return new AffineTransform(0, -1, 0, 1, 0, 0);
            default:
                throw new IllegalArgumentException("Invalid rotation: " + rotation);
        }
    }

    public static AffineTransform rotate(double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new AffineTransform(cos, sin, -sin, cos, 0, 0);
    }

    /**
     * Export this affine transform to a 4x4 column-major order matrix.
     *
     * @param matrix output 4x4 matrix
     */
    public void to4x4(float[] matrix) {
        // matrix is a 4x4 matrix in column-major order

        // Column 0
        matrix[0] = (float) a;
        matrix[1] = (float) b;
        matrix[2] = 0;
        matrix[3] = 0;

        // Column 1
        matrix[4] = (float) c;
        matrix[5] = (float) d;
        matrix[6] = 0;
        matrix[7] = 0;

        // Column 2
        matrix[8] = 0;
        matrix[9] = 0;
        matrix[10] = 1;
        matrix[11] = 0;

        // Column 3
        matrix[12] = (float) e;
        matrix[13] = (float) f;
        matrix[14] = 0;
        matrix[15] = 1;
    }

    public float[] to4x4() {
        float[] matrix = new float[16];
        to4x4(matrix);
        return matrix;
    }
}
