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
package org.apache.fontbox.util.primitive;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Objects;
import java.util.RandomAccess;


/**
 * A a fixed-size list backed by an {@code int} array. Changes to the list "write through" to the array.
 * <p>
 * Instances behave the same as and are to equal to any {@code List<Integer>} of the same size and content.
 * The list is serializable and implements {@link RandomAccess}.
 * 
 * @author Gunnar Brand
 * @since 23.07.2024
 */
public class IntArrayList extends AbstractList<Integer> implements RandomAccess, Serializable 
{
    private static final long serialVersionUID = 1L;
    
    private final int[] array;


    public IntArrayList(int[] array)
    {
        this.array = Objects.requireNonNull(array, "array");
    }
    
    
    public int[] array()
    {
        return array;
    }


    @Override
    public int size()
    {
        return array.length;
    }


    @Override
    public Integer get(int index)
    {
        return array[index];
    }


    public int getInt(int index)
    {
        return array[index];
    }

    
    @Override
    public Integer set(int index, Integer value)
    {
        return array[index] = value;
    }


    public int setInt(int index, int value)
    {
        return array[index] = value;
    }

    
    @Override
    public String toString()
    {
        return Arrays.toString(array);
    }


    @Override
    public int hashCode()
    {
        return Arrays.hashCode(array);
    }


    @Override
    public boolean equals(Object o)
    {
        return this == o || o instanceof IntArrayList ? Arrays.equals(array, ((IntArrayList)o).array) : super.equals(o);
    }
    
}
