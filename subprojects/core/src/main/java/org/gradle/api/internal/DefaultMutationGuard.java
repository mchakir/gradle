/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.internal.Factory;
import org.gradle.internal.exceptions.Contextual;

public class DefaultMutationGuard implements MutationGuard {
    private ThreadLocal<Boolean> isMutationAllowed = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.TRUE;
        }
    };

    @Override
    public boolean isMutationAllowed() {
        return isMutationAllowed.get();
    }

    @Override
    public void assertMutationAllowed(String methodName, Object target) {
        assertMutationAllowed(methodName, target, new DslObject(target).getPublicType().getConcreteClass());
    }

    @Override
    public <T> void assertMutationAllowed(String methodName, T target, Class<T> targetType) {
        if (!isMutationAllowed()) {
            throw createIllegalStateException(targetType, methodName, target);
        }
    }

    private static <T> IllegalStateException createIllegalStateException(Class<T> targetType, String methodName, T target) {
        return new IllegalMutationException(String.format("%s#%s on %s cannot be executed in the current context.", targetType.getSimpleName(), methodName, target));
    }

    @Contextual
    private static class IllegalMutationException extends IllegalStateException {
        public IllegalMutationException(String message) {
            super(message);
        }
    }

    private <T> Action<? super T> newActionWithMutation(final Action<? super T> action, final boolean allowMutationMethods) {
        return new Action<T>() {
            @Override
            public void execute(T t) {
                boolean oldIsMutationAllowed = isMutationAllowed.get();
                isMutationAllowed.set(allowMutationMethods);
                try {
                    action.execute(t);
                } finally {
                    isMutationAllowed.set(oldIsMutationAllowed);
                }
            }
        };
    }

    private void runWithMutation(final Runnable runnable, boolean allowMutationMethods) {
        boolean oldIsMutationAllowed = isMutationAllowed.get();
        isMutationAllowed.set(allowMutationMethods);
        try {
            runnable.run();
        } finally {
            isMutationAllowed.set(oldIsMutationAllowed);
        }
    }

    private <I> I createWithMutation(final Factory<I> factory, boolean allowMutationMethods) {
        boolean oldIsMutationAllowed = isMutationAllowed.get();
        isMutationAllowed.set(allowMutationMethods);
        try {
            return factory.create();
        } finally {
            isMutationAllowed.set(oldIsMutationAllowed);
        }
    }

    @Override
    public <T> Action<? super T> withMutationEnabled(Action<? super T> action) {
        return newActionWithMutation(action, true);
    }

    @Override
    public <T> Action<? super T> withMutationDisabled(Action<? super T> action) {
        return newActionWithMutation(action, false);
    }

    @Override
    public void whileMutationEnabled(Runnable runnable) {
        runWithMutation(runnable, true);
    }

    @Override
    public <T> T whileMutationEnabled(Factory<T> factory) {
        return createWithMutation(factory, true);
    }

    @Override
    public void whileMutationDisabled(Runnable runnable) {
        runWithMutation(runnable, false);
    }

    @Override
    public <T> T whileMutationDisabled(Factory<T> factory) {
        return createWithMutation(factory, false);
    }
}
