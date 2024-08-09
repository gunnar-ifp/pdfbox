/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.util;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSNumber;

/**
 * This class will be used for matrix manipulation.
 *
 * @author Ben Litchfield
 */
public final class Matrix implements Cloneable
{
	/** initial 1 */
    private float single0 = 1;
    private float single1 = 0;
    /** fixed 0 */
    private final static float single2 = 0;
    private float single3 = 0;
    /** initial 1 */
    private float single4 = 1;
    /** fixed 0 */
    private final static float single5 = 0;
    private float single6 = 0;
    private float single7 = 0;
    /** fixed 1 */
    private final static float single8 = 1;
    
    private static final float MAX_FLOAT_VALUE = 3.4028235E38f;

    /**
     * Constructor. This produces an identity matrix.
     */
    public Matrix()
    {
        // a b 0
        // c d 0
        // tx ty 1
        // note: hx and hy are reversed vs.the PDF spec as we use AffineTransform's definition x and y shear
        // sx hy 0
        // hx sy 0
        // tx ty 1
    }
    
    private Matrix(Matrix m)
    {
        single0 = m.single0;
        single1 = m.single1;
//      single2 = m.single2;
        single3 = m.single3;
        single4 = m.single4;
//      single5 = m.single5;
        single6 = m.single6;
        single7 = m.single7;
//      single8 = m.single8;
    }
    
    
    
    /**
     * Creates a matrix from a 6-element (a b c d e f) COS array.
     *
     * @param array source array, elements must be or extend COSNumber
     * 
     * @deprecated use {@link Matrix#createMatrix(COSBase)} instead
     */
    public Matrix(COSArray array)
    {
        single0 = ((COSNumber)array.getObject(0)).floatValue();
        single1 = ((COSNumber)array.getObject(1)).floatValue();
        single3 = ((COSNumber)array.getObject(2)).floatValue();
        single4 = ((COSNumber)array.getObject(3)).floatValue();
        single6 = ((COSNumber)array.getObject(4)).floatValue();
        single7 = ((COSNumber)array.getObject(5)).floatValue();
    }

    /**
     * Creates a transformation matrix with the given 6 elements. Transformation matrices are
     * discussed in 8.3.3, "Common Transformations" and 8.3.4, "Transformation Matrices" of the PDF
     * specification. For simple purposes (rotate, scale, translate) it is recommended to use the
     * static methods below.
     *
     * Produces the following matrix:
     * a b 0
     * c d 0
     * e f 1
     *
     * @see Matrix#getRotateInstance(double, float, float)
     * @see Matrix#getScaleInstance(float, float)
     * @see Matrix#getTranslateInstance(float, float)
     *
     * @param a the X coordinate scaling element (m00) of the 3x3 matrix
     * @param b the Y coordinate shearing element (m10) of the 3x3 matrix
     * @param c the X coordinate shearing element (m01) of the 3x3 matrix
     * @param d the Y coordinate scaling element (m11) of the 3x3 matrix
     * @param e the X coordinate translation element (m02) of the 3x3 matrix
     * @param f the Y coordinate translation element (m12) of the 3x3 matrix
     */
    public Matrix(float a, float b, float c, float d, float e, float f)
    {
        single0 = a;
        single1 = b;
        single3 = c;
        single4 = d;
        single6 = e;
        single7 = f;
    }

    /**
     * Creates a matrix with the same elements as the given AffineTransform.
     * @param at matrix elements will be initialize with the values from this affine transformation, as follows:
     *
     *           scaleX shearY 0
     *           shearX scaleY 0
     *           transX transY 1
     *
     */
    public Matrix(AffineTransform at)
    {
        single0 = (float)at.getScaleX();
        single1 = (float)at.getShearY();
        single3 = (float)at.getShearX();
        single4 = (float)at.getScaleY();
        single6 = (float)at.getTranslateX();
        single7 = (float)at.getTranslateY();
    }

    /**
     * Convenience method to be used when creating a matrix from unverified data. If the parameter
     * is a COSArray with at least six numbers, a Matrix object is created from the first six
     * numbers and returned. If not, then the identity Matrix is returned.
     *
     * @param base a COS object, preferably a COSArray with six numbers.
     *
     * @return a Matrix object.
     */
    public static Matrix createMatrix(COSBase base)
    {
        if (!(base instanceof COSArray))
        {
            return new Matrix();
        }
        COSArray array = (COSArray) base;
        if (array.size() < 6)
        {
            return new Matrix();
        }
        for (int i = 0; i < 6; ++i)
        {
            if (!(array.getObject(i) instanceof COSNumber))
            {
                return new Matrix();
            }
        }
        return new Matrix(array);
    }

    /**
     * This method resets the numbers in this Matrix to the original values, which are
     * the values that a newly constructed Matrix would have.
     *
     * @deprecated This method will be removed.
     */
    @Deprecated
    public void reset()
    {
        single0 = 1;
        single1 = 0;
//      single2 = 0;
        single3 = 0;
        single4 = 1;
//      single5 = 0;
        single6 = 0;
        single7 = 0;
//      single8 = 1;
    }

    /**
     * Create an affine transform from this matrix's values.
     *
     * @return An affine transform with this matrix's values.
     */
    public AffineTransform createAffineTransform()
    {
        return new AffineTransform(
            single0, single1,   // m00 m10 = scaleX shearY
            single3, single4,   // m01 m11 = shearX scaleY
            single6, single7 ); // m02 m12 = tx ty
    }

    /**
     * Set the values of the matrix from the AffineTransform.
     *
     * @param af The transform to get the values from.
     * @deprecated Use the {@link #Matrix(AffineTransform)} constructor instead.
     */
    @Deprecated
    public void setFromAffineTransform( AffineTransform af )
    {
        single0 = (float)af.getScaleX();
        single1 = (float)af.getShearY();
        single3 = (float)af.getShearX();
        single4 = (float)af.getScaleY();
        single6 = (float)af.getTranslateX();
        single7 = (float)af.getTranslateY();
    }

    /**
     * This will get a matrix value at some point.
     *
     * @param row The row to get the value from.
     * @param column The column to get the value from.
     *
     * @return The value at the row/column position.
     */
    public float getValue( int row, int column )
    {
    	switch (row * 3 + column) {
    		case 0: return single0;
    		case 1: return single1;
    		case 2: return single2;
    		case 3: return single3;
    		case 4: return single4;
    		case 5: return single5;
    		case 6: return single6;
    		case 7: return single7;
    		case 8: return single8;
    		default: throw new IndexOutOfBoundsException(row + ":" + column); 
    	}
    }

    /**
     * This will set a value at a position.
     *
     * @param row The row to set the value at.
     * @param column the column to set the value at.
     * @param value The value to set at the position.
     */
    public void setValue( int row, int column, float value )
    {
    	switch (row * 3 + column) {
    		case 0: single0 = value; break;
    		case 1: single1 = value; break;
    		case 2: if ( value != single2 ) throw new IllegalArgumentException("m[0][2] is fixed value 0") ; break;
    		case 3: single3 = value; break;
    		case 4: single4 = value; break;
    		case 5: if ( value != single5 ) throw new IllegalArgumentException("m[1][2] is fixed value 0"); break;
    		case 6: single6 = value; break;
    		case 7: single7 = value; break;
    		case 8: if ( value != single8 ) throw new IllegalArgumentException("m[2][2] is fixed value 1"); break;
    		default: throw new IndexOutOfBoundsException(row + ":" + column); 
    	}
    	
    }

    /**
     * Return a single dimension array of all values in the matrix.
     *
     * @return The values of this matrix.
     */
    public float[][] getValues()
    {
        float[][] retval = new float[3][3];
        retval[0][0] = single0;
        retval[0][1] = single1;
        retval[0][2] = single2;
        retval[1][0] = single3;
        retval[1][1] = single4;
        retval[1][2] = single5;
        retval[2][0] = single6;
        retval[2][1] = single7;
        retval[2][2] = single8;
        return retval;
    }

    /**
     * Return a single dimension array of all values in the matrix.
     *
     * @return The values ot this matrix.
     * @deprecated Use {@link #getValues()} instead.
     */
    @Deprecated
    public double[][] getValuesAsDouble()
    {
        double[][] retval = new double[3][3];
        retval[0][0] = single0;
        retval[0][1] = single1;
        retval[0][2] = single2;
        retval[1][0] = single3;
        retval[1][1] = single4;
        retval[1][2] = single5;
        retval[2][0] = single6;
        retval[2][1] = single7;
        retval[2][2] = single8;
        return retval;
    }

    /**
     * Concatenates (premultiplies) the given matrix to this matrix.
     *
     * @param matrix The matrix to concatenate.
     */
    public void concatenate(Matrix matrix)
    {
        matrix.multiply(this, this);
    }

    /**
     * Translates this matrix by the given vector.
     *
     * @param vector 2D vector
     */
    public void translate(Vector vector)
    {
        concatenate(Matrix.getTranslateInstance(vector.getX(), vector.getY()));
    }

    /**
     * Translates this matrix by the given amount.
     *
     * @param tx x-translation
     * @param ty y-translation
     */
    public void translate(float tx, float ty)
    {
        concatenate(Matrix.getTranslateInstance(tx, ty));
    }

    /**
     * Scales this matrix by the given factors.
     *
     * @param sx x-scale
     * @param sy y-scale
     */
    public void scale(float sx, float sy)
    {
        concatenate(Matrix.getScaleInstance(sx, sy));
    }

    /**
     * Rotares this matrix by the given factors.
     *
     * @param theta The angle of rotation measured in radians
     */
    public void rotate(double theta)
    {
        concatenate(Matrix.getRotateInstance(theta, 0, 0));
    }

    /**
     * This method multiplies this Matrix with the specified other Matrix, storing the product in a new instance. It is
     * allowed to have (other == this).
     *
     * @param other the second operand Matrix in the multiplication; required
     * @return the product of the two matrices.
     */
    public Matrix multiply(Matrix other)
    {
        return isFinite(multiplyArrays(this, other));
    }

    /**
     * This method multiplies this Matrix with the specified other Matrix, storing the product in the specified result
     * Matrix. It is allowed to have (other == this) or (result == this) or indeed (other == result).
     * 
     * See {@link #multiply(Matrix)} if you need a version with a single operator.
     *
     * @param other  the second operand Matrix in the multiplication; required
     * @param result the Matrix instance into which the result should be stored. If result is null, a new Matrix instance is
     *                   created.
     * @return the result.
     * 
     */
    @Deprecated
    public Matrix multiply( Matrix other, Matrix result )
    {
    	if ( result==null ) return multiply(other);
    	
    	multiplyArrays(this, other, result);
    	isFinite(result);
    	return result;
    }
    
    
    private static Matrix isFinite(Matrix result)
    {
        if (!Matrix.isFinite(result.single0)
                || !Matrix.isFinite(result.single1)
                || !Matrix.isFinite(result.single3)
                || !Matrix.isFinite(result.single4)
                || !Matrix.isFinite(result.single6)
                || !Matrix.isFinite(result.single7)
        ) {
            throw new IllegalArgumentException("Multiplying two matrices produces illegal values");
        }
        return result;
    }

    private static boolean isFinite(float f)
    {
        // this is faster than the combination of "isNaN" and "isInfinite" and Float.isFinite isn't available in java 6
        return Math.abs(f) <= MAX_FLOAT_VALUE;
    }

    private static void multiplyArrays(Matrix a, Matrix b, Matrix out)
    {
        float c0 = a.single0 * b.single0 + a.single1 * b.single3; // + a.single2 * b.single6;
        float c1 = a.single0 * b.single1 + a.single1 * b.single4; // + a.single2 * b.single7;
//      float c2 = a.single0 * b.single2 + a.single1 * b.single5; // + a.single2 * b.single8; // fixed 0: [0] * 0 + [1] * 0 + 0 * 1
        float c3 = a.single3 * b.single0 + a.single4 * b.single3; // + a.single5 * b.single6;
        float c4 = a.single3 * b.single1 + a.single4 * b.single4; // + a.single5 * b.single7;
//      float c5 = a.single3 * b.single2 + a.single4 * b.single5; // + a.single5 * b.single8; // fixed 0: [3] * 0 + [4] * 0 + 0 * 1
        float c6 = a.single6 * b.single0 + a.single7 * b.single3 + b.single6; // + a.single8 * b.single6;
        float c7 = a.single6 * b.single1 + a.single7 * b.single4 + b.single7; // + a.single8 * b.single7;
//      float c8 = a.single6 * b.single2 + a.single7 * b.single5 + a.single8 * b.single8; // fixed 1: [6] * 0 + [7] * 0 + 1 * 1
        
        out.single0 = c0;
        out.single1 = c1;
//      out.single2 = c2;
        out.single3 = c3;
        out.single4 = c4;
//      out.single5 = c5;
        out.single6 = c6;
        out.single7 = c7;
//      out.single8 = c8;
    }
    
    
    private static Matrix multiplyArrays(Matrix a, Matrix b)
    {
        return new Matrix(
            a.single0 * b.single0 + a.single1 * b.single3,
            a.single0 * b.single1 + a.single1 * b.single4,
            a.single3 * b.single0 + a.single4 * b.single3,
            a.single3 * b.single1 + a.single4 * b.single4,
            a.single6 * b.single0 + a.single7 * b.single3 + b.single6,
            a.single6 * b.single1 + a.single7 * b.single4 + b.single7
         );
    }
    

    /**
     * Transforms the given point by this matrix.
     *
     * @param point point to transform
     */
    public void transform(Point2D point)
    {
        float x = (float)point.getX();
        float y = (float)point.getY();
        float a = single0;
        float b = single1;
        float c = single3;
        float d = single4;
        float e = single6;
        float f = single7;
        point.setLocation(x * a + y * c + e, x * b + y * d + f);
    }

    /**
     * Transforms the given point by this matrix.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     *
     * @return the transformed point.
     */
    public Point2D.Float transformPoint(float x, float y)
    {
        float a = single0;
        float b = single1;
        float c = single3;
        float d = single4;
        float e = single6;
        float f = single7;
        return new Point2D.Float(x * a + y * c + e, x * b + y * d + f);
    }

    /**
     * Transforms the given vector by this matrix.
     *
     * @param vector 2D vector
     *
     * @return the transformed vector.
     */
    public Vector transform(Vector vector)
    {
        float a = single0;
        float b = single1;
        float c = single3;
        float d = single4;
        float e = single6;
        float f = single7;
        float x = vector.getX();
        float y = vector.getY();
        return new Vector(x * a + y * c + e, x * b + y * d + f);
    }

    /**
     * Create a new matrix with just the scaling operators.
     *
     * @return A new matrix with just the scaling operators.
     * @deprecated This method is due to be removed, please contact us if you make use of it.
     */
    @Deprecated
    public Matrix extractScaling()
    {
        Matrix matrix = new Matrix();
        matrix.single0 = this.single0;
        matrix.single4 = this.single4;
        return matrix;
    }

    /**
     * Convenience method to create a scaled instance.
     *
     * Produces the following matrix:
     * x 0 0
     * 0 y 0
     * 0 0 1
     *
     * @param x The xscale operator.
     * @param y The yscale operator.
     * @return A new matrix with just the x/y scaling
     */
    public static Matrix getScaleInstance(float x, float y)
    {
        return new Matrix(x, 0, 0, y, 0, 0);
    }

    /**
     * Create a new matrix with just the translating operators.
     *
     * @return A new matrix with just the translating operators.
     * @deprecated This method is due to be removed, please contact us if you make use of it.
     */
    @Deprecated
    public Matrix extractTranslating()
    {
        Matrix matrix = new Matrix();
        matrix.single6 = this.single6;
        matrix.single7 = this.single7;
        return matrix;
    }

    /**
     * Convenience method to create a translating instance.
     *
     * Produces the following matrix:
     * 1 0 0
     * 0 1 0
     * x y 1
     *
     * @param x The x translating operator.
     * @param y The y translating operator.
     * @return A new matrix with just the x/y translating.
     * @deprecated Use {@link #getTranslateInstance} instead.
     */
    @Deprecated
    public static Matrix getTranslatingInstance(float x, float y)
    {
        return new Matrix(1, 0, 0, 1, x, y);
    }

    /**
     * Convenience method to create a translating instance.
     *
     * Produces the following matrix: 1 0 0 0 1 0 x y 1
     *
     * @param x The x translating operator.
     * @param y The y translating operator.
     * @return A new matrix with just the x/y translating.
     */
    public static Matrix getTranslateInstance(float x, float y)
    {
        return new Matrix(1, 0, 0, 1, x, y);
    }

    /**
     * Convenience method to create a rotated instance.
     *
     * @param theta The angle of rotation measured in radians
     * @param tx The x translation.
     * @param ty The y translation.
     * @return A new matrix with the rotation and the x/y translating.
     */
    public static Matrix getRotateInstance(double theta, float tx, float ty)
    {
        float cosTheta = (float)Math.cos(theta);
        float sinTheta = (float)Math.sin(theta);

        return new Matrix(cosTheta, sinTheta, -sinTheta, cosTheta, tx, ty);
    }

    /**
     * Produces a copy of the first matrix, with the second matrix concatenated.
     *
     * @param a The matrix to copy.
     * @param b The matrix to concatenate.
     */
    public static Matrix concatenate(Matrix a, Matrix b)
    {
        return b.multiply(a);
    }

    /**
     * Clones this object.
     * @return cloned matrix as an object.
     */
    @Override
    public Matrix clone()
    {
        return new Matrix(this);
    }

    /**
     * Returns the x-scaling factor of this matrix. This is calculated from the scale and shear.
     *
     * @return The x-scaling factor.
     */
    public float getScalingFactorX()
    {
        /**
         * BM: if the trm is rotated, the calculation is a little more complicated
         *
         * The rotation matrix multiplied with the scaling matrix is:
         * (   x   0   0)    ( cos  sin  0)    ( x*cos x*sin   0)
         * (   0   y   0) *  (-sin  cos  0)  = (-y*sin y*cos   0)
         * (   0   0   1)    (   0    0  1)    (     0     0   1)
         *
         * So, if you want to deduce x from the matrix you take
         * M(0,0) = x*cos and M(0,1) = x*sin and use the theorem of Pythagoras
         *
         * sqrt(M(0,0)^2+M(0,1)^2) =
         * sqrt(x2*cos2+x2*sin2) =
         * sqrt(x2*(cos2+sin2)) = <- here is the trick cos2+sin2 is one
         * sqrt(x2) =
         * abs(x)
         */
        if (single1 != 0.0f)
        {
            return (float) Math.sqrt(Math.pow(single0, 2) +
                                      Math.pow(single1, 2));
        }
        return single0;
    }

    /**
     * Returns the y-scaling factor of this matrix. This is calculated from the scale and shear.
     *
     * @return The y-scaling factor.
     */
    public float getScalingFactorY()
    {
        if (single3 != 0.0f)
        {
            return (float) Math.sqrt(Math.pow(single3, 2) +
                                      Math.pow(single4, 2));
        }
        return single4;
    }

    /**
     * Returns the x-scaling element of this matrix.
     * 
     * @see #getScalingFactorX() 
     */
    public float getScaleX()
    {
        return single0;
    }

    /**
     * Returns the y-shear element of this matrix.
     */
    public float getShearY()
    {
        return single1;
    }

    /**
     * Returns the x-shear element of this matrix.
     */
    public float getShearX()
    {
        return single3;
    }

    /**
     * Returns the y-scaling element of this matrix.
     *
     * @see #getScalingFactorY()
     */
    public float getScaleY()
    {
        return single4;
    }

    /**
     * Returns the x-translation element of this matrix.
     */
    public float getTranslateX()
    {
        return single6;
    }

    /**
     * Returns the y-translation element of this matrix.
     */
    public float getTranslateY()
    {
        return single7;
    }

    /**
     * Get the x position in the matrix. This method is deprecated as it is incorrectly named.
     *
     * @return The x-position.
     * @deprecated Use {@link #getTranslateX} instead
     */
    @Deprecated
    public float getXPosition()
    {
        return single6;
    }

    /**
     * Get the y position. This method is deprecated as it is incorrectly named.
     *
     * @return The y position.
     * @deprecated Use {@link #getTranslateY} instead
     */
    @Deprecated
    public float getYPosition()
    {
        return single7;
    }

    /**
     * Returns a COS array which represent the geometric relevant
     * components of the matrix. The last column of the matrix is ignored,
     * only the first two columns are returned. This is analog to the
     * Matrix(COSArray) constructor.
     */
    public COSArray toCOSArray()
    {
        COSArray array = new COSArray();
        array.add(new COSFloat(single0));
        array.add(new COSFloat(single1));
        array.add(new COSFloat(single3));
        array.add(new COSFloat(single4));
        array.add(new COSFloat(single6));
        array.add(new COSFloat(single7));
        return array;
    }

    @Override
    public String toString()
    {
        return "[" +
            single0 + "," +
            single1 + "," +
            single3 + "," +
            single4 + "," +
            single6 + "," +
            single7 + "]";
    }

    
    @Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(single0);
		result = prime * result + Float.floatToIntBits(single1);
		result = prime * result + Float.floatToIntBits(single3);
		result = prime * result + Float.floatToIntBits(single4);
		result = prime * result + Float.floatToIntBits(single6);
		result = prime * result + Float.floatToIntBits(single7);
		return result;
	}

    
    @Override
	public boolean equals(Object obj)
	{
		if ( this == obj ) return true;
		if ( obj == null ) return false;
		if ( getClass() != obj.getClass() ) return false;
		Matrix other = (Matrix)obj;
		if ( Float.floatToIntBits(single0) != Float.floatToIntBits(other.single0) ) return false;
		if ( Float.floatToIntBits(single1) != Float.floatToIntBits(other.single1) ) return false;
		if ( Float.floatToIntBits(single3) != Float.floatToIntBits(other.single3) ) return false;
		if ( Float.floatToIntBits(single4) != Float.floatToIntBits(other.single4) ) return false;
		if ( Float.floatToIntBits(single6) != Float.floatToIntBits(other.single6) ) return false;
		if ( Float.floatToIntBits(single7) != Float.floatToIntBits(other.single7) ) return false;
		return true;
	}
}
