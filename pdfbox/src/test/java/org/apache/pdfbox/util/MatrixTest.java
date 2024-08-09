/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.awt.geom.AffineTransform;

import org.junit.Test;

/**
 *
 * @author Neil McErlean
 * @author Tilman Hausherr
 */
public class MatrixTest
{
    
    @Test
    public void testConstructionAndCopy() throws Exception
    {
        Matrix m1 = new Matrix();
        assertMatrixIsPristine(m1);

        Matrix m2 = m1.clone();
        assertNotSame(m1, m2);
        assertMatrixIsPristine(m2);
    }

    @Test
    public void testAffineTransform() throws Exception
    {
        Matrix m1 = new Matrix(1, 2, 3, 4, 5, 6);
        AffineTransform af = m1.createAffineTransform();
        assertEquals(1d, af.getScaleX(), 0d);
        assertEquals(2d, af.getShearY(), 0d);
        assertEquals(3d, af.getShearX(), 0d);
        assertEquals(4d, af.getScaleY(), 0d);
        assertEquals(5d, af.getTranslateX(), 0d);
        assertEquals(6d, af.getTranslateY(), 0d);

        Matrix m2 = new Matrix(af);
        assertEquals(m1,  m2);
    }
    
    @Test
    public void testGetScalingFactor()
    {
        // check scaling factor of an initial matrix
        Matrix m1 = new Matrix();
        assertEquals(1, m1.getScalingFactorX(), 0);
        assertEquals(1, m1.getScalingFactorY(), 0);

        // check scaling factor of an initial matrix
        Matrix m2 = new Matrix(2, 4, 4, 2, 0, 0);
        assertEquals((float) Math.sqrt(20), m2.getScalingFactorX(), 0);
        assertEquals((float) Math.sqrt(20), m2.getScalingFactorY(), 0);
    }

    @Test
    public void testCreateMatrixUsingInvalidInput()
    {
        // anything but a COSArray is invalid and leads to an initial matrix
        Matrix createMatrix = Matrix.createMatrix(COSName.A);
        assertMatrixIsPristine(createMatrix);

        // a COSArray with fewer than 6 entries leads to an initial matrix
        COSArray cosArray = new COSArray();
        cosArray.add(COSName.A);
        createMatrix = Matrix.createMatrix(cosArray);
        assertMatrixIsPristine(createMatrix);

        // a COSArray containing other kind of objects than COSNumber leads to an initial matrix
        cosArray = new COSArray();
        for (int i = 0; i < 6; i++)
        {
            cosArray.add(COSName.A);
        }
        createMatrix = Matrix.createMatrix(cosArray);
        assertMatrixIsPristine(createMatrix);
    }

    @Test
    public void testMultiplication()
    {
        // These matrices will not change - we use it to drive the various multiplications.
        final Matrix const1 = new Matrix();
        final Matrix const2 = new Matrix();

        // Create matrix with values
        // 0, 1, 0
        // 1, 2, 0
        // 2, 3, 1
        for (int x = 0; x < 3; x++)
        {
            for (int y = 0; y < 2; y++)
            {
                const1.setValue(x, y, x + y);
                const2.setValue(x, y, 8 + x + y);
            }
        }

        float[] m1MultipliedByM1 = multiply(const1, const1);
        float[] m1MultipliedByM2 = multiply(const1, const2);
        float[] m2MultipliedByM1 = multiply(const2, const1);

        Matrix var1 = const1.clone();
        Matrix var2 = const2.clone();

        // Multiply two matrices together producing a new result matrix.
        Matrix result = var1.multiply(var2);
        assertEquals(const1, var1);
        assertEquals(const2, var2);
        assertMatrixValuesEqualTo(m1MultipliedByM2, result);

        // Multiply two matrices together with the result being written to a third matrix
        // (Any existing values there will be overwritten).
        result = var1.multiply(var2);
        assertEquals(const1, var1);
        assertEquals(const2, var2);
        assertMatrixValuesEqualTo(m1MultipliedByM2, result);

        // Multiply two matrices together with the result being written into 'this' matrix
        var1 = const1.clone();
        var2 = const2.clone();
        var1.concatenate(var2);
        assertEquals(const2, var2);
        assertMatrixValuesEqualTo(m2MultipliedByM1, var1);

        var1 = const1.clone();
        var2 = const2.clone();
        result = Matrix.concatenate(var1, var2);
        assertEquals(const1, var1);
        assertEquals(const2, var2);
        assertMatrixValuesEqualTo(m2MultipliedByM1, result);

        // Multiply the same matrix with itself with the result being written into 'this' matrix
        var1 = const1.clone();
        result = var1.multiply(var1);
        assertEquals(const1, var1);
        assertMatrixValuesEqualTo(m1MultipliedByM1, result);
    }

    @Test
    public void testOldMultiplication() throws Exception
    {
        // This matrix will not change - we use it to drive the various multiplications.
        final Matrix testMatrix = new Matrix();

        // Create matrix with values
        // 0, 1, 0
        // 1, 2, 0
        // 2, 3, 1
        for (int x = 0; x < 3; x++)
        {
            for (int y = 0; y < 2; y++)
            {
                testMatrix.setValue(x, y, x + y);
            }
        }

        Matrix m1 = testMatrix.clone();
        Matrix m2 = testMatrix.clone();

        // Multiply two matrices together producing a new result matrix.
        float[] ref = multiply(m1, m2);
        Matrix product = m1.multiply(m2);

        assertNotSame(m1, product);
        assertNotSame(m2, product);

        // Operand 1 should not have changed
        assertMatrixValuesEqualTo(new float[] { 0, 1, 0, 1, 2, 0, 2, 3, 1 }, m1);
        // Operand 2 should not have changed
        assertMatrixValuesEqualTo(new float[] { 0, 1, 0, 1, 2, 0, 2, 3, 1 }, m2);
        assertMatrixValuesEqualTo(ref, product);

        // Multiply two matrices together with the result being written to a third matrix
        // (Any existing values there will be overwritten).
        Matrix resultMatrix = new Matrix();

        Matrix retVal = m1.multiply(m2, resultMatrix);
        assertSame(retVal, resultMatrix);
        // Operand 1 should not have changed
        assertMatrixValuesEqualTo(new float[] { 0, 1, 0, 1, 2, 0, 2, 3, 1 }, m1);
        // Operand 2 should not have changed
        assertMatrixValuesEqualTo(new float[] { 0, 1, 0, 1, 2, 0, 2, 3, 1 }, m2);
        assertMatrixValuesEqualTo(ref, resultMatrix);

        // Multiply two matrices together with the result being written into the other matrix
        retVal = m1.multiply(m2, m2);
        assertSame(retVal, m2);
        // Operand 1 should not have changed
        assertMatrixValuesEqualTo(new float[] { 0, 1, 0, 1, 2, 0, 2, 3, 1 }, m1);
        assertMatrixValuesEqualTo(ref, retVal);

        // Multiply two matrices together with the result being written into 'this' matrix
        m1 = testMatrix.clone();
        m2 = testMatrix.clone();

        ref = multiply(m1, m2);
        retVal = m1.multiply(m2, m1);
        assertSame(retVal, m1);
        // Operand 2 should not have changed
        assertMatrixValuesEqualTo(new float[] { 0, 1, 0, 1, 2, 0, 2, 3, 1 }, m2);
        assertMatrixValuesEqualTo(ref, retVal);

        // Multiply the same matrix with itself with the result being written into 'this' matrix
        m1 = testMatrix.clone();

        ref = multiply(m1, m1);
        retVal = m1.multiply(m1, m1);
        assertSame(retVal, m1);
        assertMatrixValuesEqualTo(ref, retVal);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalValueNaN1()
    {
        Matrix m = new Matrix();
        m.setValue(0, 0, Float.MAX_VALUE);
        m.multiply(m, m);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalValueNaN2()
    {
        Matrix m = new Matrix();
        m.setValue(0, 0, Float.NaN);
        m.multiply(m, m);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalValuePositiveInfinity()
    {
        Matrix m = new Matrix();
        m.setValue(0, 0, Float.POSITIVE_INFINITY);
        m.multiply(m, m);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalValueNegativeInfinity()
    {
        Matrix m = new Matrix();
        m.setValue(0, 0, Float.NEGATIVE_INFINITY);
        m.multiply(m, m);
    }

    /**
     * Test of PDFBOX-2872 bug
     */
    @Test
    public void testPdfbox2872()
    {
        Matrix m = new Matrix(2, 4, 5, 8, 2, 0);
        COSArray toCOSArray = m.toCOSArray();
        assertEquals(new COSFloat(2), toCOSArray.get(0));
        assertEquals(new COSFloat(4), toCOSArray.get(1));
        assertEquals(new COSFloat(5), toCOSArray.get(2));
        assertEquals(new COSFloat(8), toCOSArray.get(3));
        assertEquals(new COSFloat(2), toCOSArray.get(4));
        assertEquals(new COSFloat(0), toCOSArray.get(5));
        
    }

    @Test
    public void testGetValues()
    {
        Matrix m = new Matrix(2, 4, 4, 2, 15, 30);
        float[][] values = m.getValues();
        assertEquals(2, values[0][0], 0);
        assertEquals(4, values[0][1], 0);
        assertEquals(0, values[0][2], 0);
        assertEquals(4, values[1][0], 0);
        assertEquals(2, values[1][1], 0);
        assertEquals(0, values[1][2], 0);
        assertEquals(15, values[2][0], 0);
        assertEquals(30, values[2][1], 0);
        assertEquals(1, values[2][2], 0);
    }

    @Test
    public void testScaling()
    {
        Matrix m = new Matrix(2, 4, 4, 2, 15, 30);
        m.scale(2, 3);
        // first row, multiplication with 2
        assertEquals(4, m.getValue(0, 0), 0);
        assertEquals(8, m.getValue(0, 1), 0);
        assertEquals(0, m.getValue(0, 2), 0);

        // second row, multiplication with 3
        assertEquals(12, m.getValue(1, 0), 0);
        assertEquals(6, m.getValue(1, 1), 0);
        assertEquals(0, m.getValue(1, 2), 0);

        // third row, no changes at all
        assertEquals(15, m.getValue(2, 0), 0);
        assertEquals(30, m.getValue(2, 1), 0);
        assertEquals(1, m.getValue(2, 2), 0);
    }

    @Test
    public void testTranslation()
    {
        Matrix m = new Matrix(2, 4, 4, 2, 15, 30);
        m.translate(2, 3);
        // first row, no changes at all
        assertEquals(2, m.getValue(0, 0), 0);
        assertEquals(4, m.getValue(0, 1), 0);
        assertEquals(0, m.getValue(0, 2), 0);

        // second row, no changes at all
        assertEquals(4, m.getValue(1, 0), 0);
        assertEquals(2, m.getValue(1, 1), 0);
        assertEquals(0, m.getValue(1, 2), 0);

        // third row, translated values
        assertEquals(31, m.getValue(2, 0), 0);
        assertEquals(44, m.getValue(2, 1), 0);
        assertEquals(1, m.getValue(2, 2), 0);
    }

    /**
     * This method asserts that the matrix values for the given {@link Matrix} object are equal to the pristine, or
     * original, values.
     * 
     * @param m the Matrix to test.
     */
    private void assertMatrixIsPristine(Matrix m)
    {
        assertMatrixValuesEqualTo(new float[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 }, m);
    }

    /**
     * This method asserts that the matrix values for the given {@link Matrix} object have the specified values.
     * 
     * @param expected the expected values
     * @param m the matrix to test
     */
    private void assertMatrixValuesEqualTo(float[] expected, Matrix m)
    {
        float delta = 0.00001f;
        for (int i = 0; i < expected.length; i++) {
            int row = i / 3;
            int column = i % 3;
            String msg = String.format("Incorrect value for matrix[%d, %d]", row, column);
            assertEquals(msg, expected[i], m.getValue(row, column), delta);
        }
    }
    
    //Uncomment annotation to run the test
    //@Test
    public void testMultiplicationPerformance() {
        long start = System.currentTimeMillis();
        Matrix c;
        Matrix d;
        for (long i = 0; i<2_000_000_000l; i++) {
            c = new Matrix(15, 3, 235, 55, 422, 1);
            d = new Matrix(45, 345, 23, 551, 66, 832);
            c.multiply(d);
            c.concatenate(d);
        }
        long stop = System.currentTimeMillis();
        System.out.println("Matrix multiplication took " + (stop - start) + "ms.");
    }
    
    
    private static float[] multiply(Matrix m1, Matrix m2)
    {
        // a00 = a00 * b00 + a01 * b10 + a02 * b20 -> a0 = a0 * b0 + a1 * b3 + a2 * b6
        // a01 = a00 * b01 + a01 * b11 + a02 * b21 -> a1 = a0 * b1 + a1 * b4 + a2 * b7
        // a02 = a00 * b02 + a01 * b12 + a02 * b22 -> a2 = a0 * b2 + a1 * b5 + a2 * b8
        // a10 = a10 * b00 + a11 * b10 + a12 * b20 -> a3 = a3 * b0 + a4 * b3 + a5 * b6
        // a11 = a10 * b01 + a11 * b11 + a12 * b21 -> a4 = a3 * b1 + a4 * b4 + a5 * b7
        // a12 = a10 * b02 + a11 * b12 + a12 * b22 -> a5 = a3 * b2 + a4 * b5 + a5 * b8
        // a20 = a20 * b00 + a21 * b10 + a22 * b20 -> a6 = a6 * b0 + a7 * b3 + a8 * b6
        // a21 = a20 * b01 + a21 * b11 + a22 * b21 -> a7 = a6 * b1 + a7 * b4 + a8 * b7
        // a22 = a20 * b02 + a21 * b12 + a22 * b22 -> a8 = a6 * b2 + a7 * b5 + a8 * b8

        float[] a = flatten(m1);
        float[] b = flatten(m2);
        return new float[] {
            a[0] * b[0] + a[1] * b[3] + a[2] * b[6],
            a[0] * b[1] + a[1] * b[4] + a[2] * b[7],
            a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
            a[3] * b[0] + a[4] * b[3] + a[5] * b[6],
            a[3] * b[1] + a[4] * b[4] + a[5] * b[7],
            a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
            a[6] * b[0] + a[7] * b[3] + a[8] * b[6],
            a[6] * b[1] + a[7] * b[4] + a[8] * b[7],
            a[6] * b[2] + a[7] * b[5] + a[8] * b[8]
        };
    }

    
    private static float[] flatten(Matrix m)
    {
        return new float[] {
            m.getScaleX(),     m.getShearY(),     0,
            m.getShearX(),     m.getScaleY(),     0,
            m.getTranslateX(), m.getTranslateY(), 1 };
    }
    
}
