/*
 * Copyright 2026 Alexei Sischin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alexei.sischin.obfuscatewg.cli.config;

import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ConfigSourceTest<T extends ConfigSource> {

    protected T victim;

    protected void get_givenValues_returnsThem(Map<ConfigProperty, String> expectedValues) {
        expectedValues.forEach((key, expectedValue) -> {
            Optional<String> actualValue = victim.get(key);
            assertThat(actualValue).contains(expectedValue);
        });
    }

    protected void get_givenValues_returnsEmptyForOthers(Map<ConfigProperty, String> expectedValues) {
        Arrays.stream(ConfigProperty.values())
                .filter(Predicate.not(expectedValues::containsKey))
                .forEach(otherPropKey -> {
                    Optional<String> actualValue = victim.get(otherPropKey);
                    assertThat(actualValue).isEmpty();
                });
    }

    @EnumSource(ConfigProperty.class)
    @ParameterizedTest
    protected void get_givenNoValues_returnsEmpty(ConfigProperty propKey) {
        Optional<String> actualValue = victim.get(propKey);
        assertThat(actualValue).isEmpty();
    }
}
