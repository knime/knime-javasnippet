/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Sep 11, 2024 (wiswedel): created
 */
package org.knime.ext.sun.nodes.script.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.knime.core.data.DataTableSpec;
import org.knime.ext.sun.nodes.script.settings.JavaScriptingCustomizer;
import org.knime.ext.sun.nodes.script.settings.JavaScriptingSettings;

/**
 * Tests for {@link Expression}, started by AP-19656.
 *
 * @author wiswedel
 */
final class ExpressionTest {

    @SuppressWarnings("static-method")
    @Test
    void testDeletionOfTempFolder() throws Exception {
        final JavaScriptingCustomizer customizer = new JavaScriptingCustomizer();
        final JavaScriptingSettings settings1 = customizer.createSettings();
        settings1.setReturnType(String.class.getName());
        settings1.setExpression("return \"Test\";");
        settings1.setInputAndCompile(new DataTableSpec());
        try (final Expression compiledExpression = settings1.getCompiledExpression()) {
            final ExpressionInstance instance = compiledExpression.getInstance();
            instance.set(Map.of());
            assertEquals("Test", instance.evaluate());
        }

        final Optional<File> tempClassPathFolder1 = Expression.getTempClassPathFolder();
        assertTrue(tempClassPathFolder1.isPresent(), "Temp folder assigned");
        assertTrue(tempClassPathFolder1.get().isDirectory(), "Temp folder exists");
        Collection<File> filesInFolderRecursive =
            FileUtils.listFiles(tempClassPathFolder1.orElseThrow(), new String[]{"class"}, true);
        assertTrue(!filesInFolderRecursive.isEmpty(), "Temp folder contains .class files");
        filesInFolderRecursive.stream().limit(1).forEach(File::delete);

        // does not fail, everything cached
        try (final Expression compiledExpression = settings1.getCompiledExpression()) {
            final ExpressionInstance instance = compiledExpression.getInstance();
            instance.set(Map.of());
            assertEquals("Test", instance.evaluate());
        }

        final Optional<File> tempClassPathFolder2 = Expression.getTempClassPathFolder();
        assertTrue(tempClassPathFolder2.isPresent(), "Temp folder assigned");
        assertEquals(tempClassPathFolder1.orElseThrow(), tempClassPathFolder2.orElseThrow(),
            "Temp folder location not changed yet");

        final JavaScriptingSettings settings2 = customizer.createSettings();
        settings2.setReturnType(String.class.getName());
        settings2.setExpression("return \"Bar\";");
        settings2.setInputAndCompile(new DataTableSpec());
        try (final Expression compiledExpression = settings2.getCompiledExpression()) {
            final ExpressionInstance instance = compiledExpression.getInstance();
            instance.set(Map.of());
            assertEquals("Bar", instance.evaluate());
        }

        final Optional<File> tempClassPathFolder3 = Expression.getTempClassPathFolder();
        assertTrue(tempClassPathFolder3.isPresent(), "Temp folder assigned");
        assertNotEquals(tempClassPathFolder1.orElseThrow(), tempClassPathFolder3.orElseThrow(),
            "Temp folder location changed after new compilation");

        assertTrue(!tempClassPathFolder1.get().exists(), "Old temp folder deleted now");
    }
}
