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

package alexei.sischin.obfuscatewg.core._test;

import lombok.experimental.UtilityClass;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
public class ArgUtils {

    public static Stream<Arguments> dropoutArgStream(Object... prototypeArgs) {
        return IntStream.range(0, prototypeArgs.length)
                .mapToObj(i -> {
                    Object[] argArrayWithNull = Arrays.copyOf(prototypeArgs, prototypeArgs.length);
                    argArrayWithNull[i] = null;
                    return argArrayWithNull;
                })
                .map(Arguments::of);
    }
}
