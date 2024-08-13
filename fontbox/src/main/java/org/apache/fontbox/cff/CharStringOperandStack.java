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
package org.apache.fontbox.cff;

import java.util.Arrays;
import java.util.function.DoublePredicate;


/**
 * Numerical operand stack for Type1 and Type2 charstrings.
 * <p>
 * TODO: Maybe should be a ring buffer for better front getters...
 * 
 * @author Gunnar Brand
 */
public final class CharStringOperandStack
{
    private final static double[] EMPTY = {};

    private double[] _data = EMPTY;
    private int _size = 0;
    private int _offset = 0;


    public CharStringOperandStack()
    {
        _data = EMPTY;
    }


    public CharStringOperandStack(int size)
    {
        _data = size == 0 ? EMPTY : new double[size];
    }


    public void ensureCapacity(int size)
    {
        if ( size>_data.length ) {
            _data = Arrays.copyOfRange(_data, _offset, _offset + Math.max(8, Math.max(size, _size * 2)));
            _offset = 0;
        }
        else if ( _offset>0 ) {
            if ( _size>0 ) System.arraycopy(_data, _offset, _data, 0, _size);
            _offset = 0;
        }
    }


    public void clear()
    {
        _offset = 0;
        _size = 0;
    }


    public void discard()
    {
        clear();
        if ( _data.length > 8 ) _data = EMPTY;
    }


    public boolean isEmpty()
    {
        return _size == 0;
    }


    public int size()
    {
        return _size;
    }


    /**
     * Inserts value at the given index from the bottom up.
     * @param index Index to insert at, must be less or equal to size of stack.
     */
    public void insert(final int index, double value) throws ArrayIndexOutOfBoundsException
    {
        if ( index==_size ) {
            push(value);
        }
        else if ( index<0 || index>_size ) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        else {
            ensureCapacity(_size + 1);
            System.arraycopy(_data, _offset + index, _data, _offset + index + 1, _size - index);
            _data[_offset + index ] = value;
        }
    }


    /**
     * Removes value at the given index from the bottom up.
     */
    public double remove(final int index) throws ArrayIndexOutOfBoundsException
    {
        if ( index==0 ) return pull();
        if ( index<0 || index>=_size ) throw new ArrayIndexOutOfBoundsException(index);
        final int offset = _offset + index;
        final double d = _data[offset];
        if ( --_size>index ) System.arraycopy(_data, offset + 1, _data, offset, _size - index);
        return d;
    }


    /**
     * Adds a new element to the top of the stack.
     */
    public void push(double value)
    {
        ensureCapacity(_size + 1);
        _data[_offset + _size] = value;
        _size++;
    }


    /**
     * Returns but does not remove the top element from the stack.
     */
    public double peek() throws ArrayIndexOutOfBoundsException
    {
        return _data[_offset + _size - 1];
    }


    /**
     * Returns but does not remove the top element from the stack.
     */
    public float peekFloat() throws ArrayIndexOutOfBoundsException
    {
        return (float)_data[_offset + _size - 1];
    }


    /**
     * Returns but does not remove the top element from the stack.
     */
    public int peekInt() throws ArrayIndexOutOfBoundsException, IllegalArgumentException
    {
        return asInt(_data[_offset + _size - 1]);
    }


    /**
     * Pops top element from the stack
     */
    public double pop() throws ArrayIndexOutOfBoundsException
    {
        if ( _size == 0 ) throw new ArrayIndexOutOfBoundsException(-1);
        return _data[--_size + _offset];
    }


    /**
     * Pops top element from the stack
     */
    public float popFloat() throws ArrayIndexOutOfBoundsException
    {
        return (float)pop();
    }


    /**
     * Pops top element from the stack
     */
    public int popInt() throws ArrayIndexOutOfBoundsException, IllegalArgumentException
    {
        return asInt(pop());
    }


    /**
     * Pulls bottom element out of the stack.
     */
    public double pull() throws ArrayIndexOutOfBoundsException
    {
        if ( _size == 0 ) throw new ArrayIndexOutOfBoundsException(-1);
        _size--;
        return _data[_offset++];
    }


    /**
     * Pulls bottom element out of the stack.
     */
    public float pullFloat() throws ArrayIndexOutOfBoundsException
    {
        return (float)pull();
    }


    /**
     * Pulls bottom element out of the stack.
     */
    public int pullInt() throws ArrayIndexOutOfBoundsException, IllegalArgumentException
    {
        return asInt(pull());
    }


    public double getTop(int index) throws ArrayIndexOutOfBoundsException
    {
        if ( index >= _size ) throw new ArrayIndexOutOfBoundsException(index);
        return _data[_offset + (_size - index - 1)];
    }


    public double getBottom(int index) throws ArrayIndexOutOfBoundsException
    {
        if ( index >= _size ) throw new ArrayIndexOutOfBoundsException(index);
        return _data[_offset + index];
    }


    public double get(int index) throws ArrayIndexOutOfBoundsException
    {
        if ( index >= _size ) throw new ArrayIndexOutOfBoundsException(index);
        return _data[_offset + index];
    }


    public double set(int index, double value) throws ArrayIndexOutOfBoundsException
    {
        if ( index >= _size ) throw new ArrayIndexOutOfBoundsException(index);
        double old = _data[_offset + index];
        _data[_offset + index] = value;
        return old;
    }


    public boolean forEach(DoublePredicate cb)
    {
        for ( int i = _offset, e = _offset + _size; i < e; i++ ) {
            if ( !cb.test(_data[i]) ) return false;
        }
        return true;
    }


    public static int asInt(double value) throws IllegalArgumentException
    {
        int integer = (int)value;
        if ( value != integer ) throw new IllegalArgumentException(value + " is not an integer");
        return integer;
    }


    /**
     * Fluid operation that clears the stack.
     */
    public CharStringOperandStack set()
    {
        clear();
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given value.
     */
    public CharStringOperandStack set(double value)
    {
        clear();
        push(value);
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given values.
     */
    public CharStringOperandStack set2(double val1, double val2)
    {
        clear();
        ensureCapacity(_size + 2);
        _size += 2;
        int offset = _offset;
        _data[offset++] = val1;
        _data[offset++] = val2;
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given values.
     */
    public CharStringOperandStack set3(double val1, double val2, double val3)
    {
        clear();
        ensureCapacity(_size + 3);
        _size += 3;
        int offset = _offset;
        _data[offset++] = val1;
        _data[offset++] = val2;
        _data[offset++] = val3;
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given values.
     */
    public CharStringOperandStack set4(double val1, double val2, double val3, double val4)
    {
        clear();
        ensureCapacity(_size + 4);
        _size += 4;
        int offset = _offset;
        _data[offset++] = val1;
        _data[offset++] = val2;
        _data[offset++] = val3;
        _data[offset++] = val4;
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given values.
     */
    public CharStringOperandStack set5(double val1, double val2, double val3, double val4, double val5)
    {
        clear();
        ensureCapacity(_size + 5);
        _size += 5;
        int offset = _offset;
        _data[offset++] = val1;
        _data[offset++] = val2;
        _data[offset++] = val3;
        _data[offset++] = val4;
        _data[offset++] = val5;
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given values.
     */
    public CharStringOperandStack set6(double val1, double val2, double val3, double val4, double val5, double val6)
    {
        clear();
        ensureCapacity(_size + 6);
        _size += 6;
        int offset = _offset;
        _data[offset++] = val1;
        _data[offset++] = val2;
        _data[offset++] = val3;
        _data[offset++] = val4;
        _data[offset++] = val5;
        _data[offset++] = val6;
        return this;
    }


    /**
     * Fluid operation that clears the stack and pushes the given value2.
     */
    public CharStringOperandStack set(CharStringOperandStack other, int size)
    {
        final int count = Math.min(other._size, size);
        clear();
        if ( count>0 ) {
            ensureCapacity(_size + count);
            System.arraycopy(other._data, other._offset, _data, _offset + _size, count);
            _size += count;
            other._size -= count; 
            other._offset = other._size==0 ? 0 : other._offset + count;
        }
        if ( count<size ) throw new ArrayIndexOutOfBoundsException(count);
        return this;
    }

}
