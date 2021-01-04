/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.model;

import org.gradle.api.Incubating;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks removed methods in the Gradle API.
 *
 * <p>This annotation represents an intermediate state between deprecation actual deletion. The annotated methods are stripped from the generated API jar and from the Javadoc. Plugins won't see the
 * removed methods at compile time. At the same time, they are still present in the Gradle runtime to maintain backward compatibility with existing plugins.</p>
 *
 * <p>The policy is to delete methods with @Removed annotation in the next major Gradle release.</p>
 *
 * @since 7.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Incubating
public @interface Removed {

    /**
     * The Gradle version that introduced the annotation.
     */
    String version();
}
