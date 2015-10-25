/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.resolve;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.LibrarySpec;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface LibraryResolutionErrorMessageBuilder {
    String multipleCompatibleVariantsErrorMessage(String libraryName, Collection<? extends BinarySpec> binaries);

    String noCompatibleBinaryErrorMessage(String libraryName, Collection<BinarySpec> allBinaries);

    /**
     * Intermediate data structure used to store the result of a resolution and help at building an understandable error message in case resolution fails.
     */
    class LibraryResolutionResult {
        private static final LibraryResolutionResult EMPTY = new LibraryResolutionResult();
        private static final LibraryResolutionResult PROJECT_NOT_FOUND = new LibraryResolutionResult();
        private final Map<String, LibrarySpec> libsMatchingRequirements;
        private final Map<String, LibrarySpec> libsNotMatchingRequirements;

        private LibrarySpec selectedLibrary;
        private LibrarySpec nonMatchingLibrary;

        private LibraryResolutionResult() {
            this.libsMatchingRequirements = Maps.newHashMap();
            this.libsNotMatchingRequirements = Maps.newHashMap();
        }

        private LibrarySpec getSingleMatchingLibrary() {
            if (libsMatchingRequirements.size() == 1) {
                return libsMatchingRequirements.values().iterator().next();
            }
            return null;
        }

        private void resolve(String libraryName) {
            if (libraryName == null) {
                LibrarySpec singleMatchingLibrary = getSingleMatchingLibrary();
                if (singleMatchingLibrary == null) {
                    return;
                }
                libraryName = singleMatchingLibrary.getName();
            }

            selectedLibrary = libsMatchingRequirements.get(libraryName);
            nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName);
        }

        public boolean isProjectNotFound() {
            return PROJECT_NOT_FOUND == this;
        }

        public boolean hasLibraries() {
            return !libsMatchingRequirements.isEmpty() || !libsNotMatchingRequirements.isEmpty();
        }

        public LibrarySpec getSelectedLibrary() {
            return selectedLibrary;
        }

        public LibrarySpec getNonMatchingLibrary() {
            return nonMatchingLibrary;
        }

        public List<String> getCandidateLibraries() {
            return Lists.newArrayList(libsMatchingRequirements.keySet());
        }

        public String toResolutionErrorMessage(
            Class<? extends BinarySpec> binaryType,
            LibraryComponentSelector selector) {
            List<String> candidateLibraries = formatLibraryNames(getCandidateLibraries());
            String projectPath = selector.getProjectPath();
            String libraryName = selector.getLibraryName();

            StringBuilder sb = new StringBuilder("Project '").append(projectPath).append("'");
            if (libraryName == null || !hasLibraries()) {
                if (isProjectNotFound()) {
                    sb.append(" not found.");
                } else if (!hasLibraries()) {
                    sb.append(" doesn't define any library.");
                } else {
                    sb.append(" contains more than one library. Please select one of ");
                    Joiner.on(", ").appendTo(sb, candidateLibraries);
                }
            } else {
                LibrarySpec notMatchingRequirements = getNonMatchingLibrary();
                if (notMatchingRequirements != null) {
                    sb.append(" contains a library named '").append(libraryName)
                        .append("' but it doesn't have any binary of type ")
                        .append(binaryType.getSimpleName());
                } else {
                    sb.append(" does not contain library '").append(libraryName).append("'. Did you want to use ");
                    if (candidateLibraries.size() == 1) {
                        sb.append(candidateLibraries.get(0));
                    } else {
                        sb.append("one of ");
                        Joiner.on(", ").appendTo(sb, candidateLibraries);
                    }
                    sb.append("?");
                }
            }
            return sb.toString();
        }

        public static LibraryResolutionResult of(Collection<? extends LibrarySpec> libraries, String libraryName, Predicate<? super LibrarySpec> libraryFilter) {
            LibraryResolutionResult result = new LibraryResolutionResult();
            for (LibrarySpec librarySpec : libraries) {
                if (libraryFilter.apply(librarySpec)) {
                    result.libsMatchingRequirements.put(librarySpec.getName(), librarySpec);
                } else {
                    result.libsNotMatchingRequirements.put(librarySpec.getName(), librarySpec);
                }
            }
            result.resolve(libraryName);
            return result;
        }

        public static LibraryResolutionResult projectNotFound() {
            return LibraryResolutionResult.PROJECT_NOT_FOUND;
        }

        public static LibraryResolutionResult emptyResolutionResult() {
            return LibraryResolutionResult.EMPTY;
        }

        private static List<String> formatLibraryNames(List<String> libs) {
            List<String> list = Lists.transform(libs, new Function<String, String>() {
                @Override
                public String apply(String input) {
                    return String.format("'%s'", input);
                }
            });
            return Ordering.natural().sortedCopy(list);
        }
    }

}
