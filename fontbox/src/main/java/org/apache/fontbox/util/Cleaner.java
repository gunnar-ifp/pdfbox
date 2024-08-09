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
package org.apache.fontbox.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility code for cleaning of resources in caches / reference queues until switch to JDK 9+.
 * 
 * @author Gunnar Brand
 * @since 15.07.2024
 */
@FunctionalInterface
public interface Cleaner
{
    @FunctionalInterface
    public interface Cleanable
    {
        void clean();
    }
    
    Cleanable cleanable();
    
    
    static Cleanable silent(CheckedRunnable<?> action)
    {
        return () -> {
            try {
                action.run();
            }
            catch (Exception t) {
            }
        };
    }


    static Cleanable quiet(CheckedRunnable<?> action)
    {
        return () -> {
            try {
                action.run();
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception t) {
            }
        };
    }

    
    @FunctionalInterface
    interface CheckedRunnable<X extends Exception>
    {
        void run() throws X;
    }
    
    
    static interface CleanableReference
    {
        Cleanable clearCleanable();

        default void clean() {
            Cleanable cleanable = clearCleanable();
            if ( cleanable != null ) {
                try {
                    cleanable.clean();
                } catch (RuntimeException e) {
                }
            }
        }
        
        static void cleanQueue(ReferenceQueue<?> queue) {
            for ( Reference<?> ref = queue.poll(); ref != null; ref = queue.poll() ) {
                if ( ref instanceof CleanableReference ) ((CleanableReference)ref).clean();
            }
        }
            
        static Cleanable getCleanable(Object obj) {
            return obj instanceof Cleaner ? ((Cleaner)obj).cleanable() : null;
        }
    }
    
    
    static class CleanableSoftReference<T> extends SoftReference<T> implements CleanableReference 
    {
        private final AtomicReference<Cleanable> cleanable;

        public CleanableSoftReference(T referent) {
            this(referent, null, CleanableReference.getCleanable(referent));
        }
        
        public CleanableSoftReference(T referent, ReferenceQueue<? super T> queue) {
            this(referent, queue, CleanableReference.getCleanable(referent));
        }
        
        private CleanableSoftReference(T referent, ReferenceQueue<? super T> queue, Cleanable cleaner) {
            super(referent, cleaner == null ? null : queue);
            this.cleanable = cleaner == null ? null : new AtomicReference<>(cleaner);
        }

        @Override
        public Cleanable clearCleanable() {
            return cleanable == null ? null : cleanable.getAndSet(null);
        }
        
        @Override
        public void clear() {
            super.clear();
            clean();
        }
    }
    
}
