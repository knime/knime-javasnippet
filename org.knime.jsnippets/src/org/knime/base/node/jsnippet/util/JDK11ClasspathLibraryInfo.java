package org.knime.base.node.jsnippet.util;

import org.fife.rsta.ac.java.PackageMapNode;
import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.rsta.ac.java.classreader.ClassFile;
import org.knime.core.node.NodeLogger;

import java.io.IOException;

/**
 * @since 4.4
 */
public class JDK11ClasspathLibraryInfo extends LibraryInfo {

    NodeLogger LOGGER = NodeLogger.getLogger(JDK11ClasspathLibraryInfo.class);

    /**
     * Constructor.
     *
     * This may be <code>null</code>.
     */
    public JDK11ClasspathLibraryInfo() {

        // Since we can assume that information on JDK classes does not change, we can load the info once and re-use
        // that information across different nodes and invokations.
        // To this end, we will only use a single instance of this class as a static final field of
        // JavaSnippetNodeDialog; see JavaSnippetNodeDialog#JDK_LIBRARY_INFO and its usages.

        // As such, here in the constructor, we need to read information from the FS and populate a cache.

        // What we want are all the JDK classes. Since Java 9, these are not contained in a simple jar anymore (`rt
        // .jar`). Instead, you can access them via a special `jrt:/` FileSystem as demonstrated in the example here:
        //    https://stackoverflow.com/a/54142975/13890284
        //  We need to gather all `.class` files in `modules` (or any subdirectory thereof), so, use only "modules"
        //  as a path.

        // To create the FileSystem, you will need to supply a URLClassLoader that can load the classes which
        // implement the special file system provider. These are located at the path `<JRE dir>/lib/jrt-fs.jar`,
        // which we can pass as a parameter to the URLClassLoader constructor. You can find <JRE dir> via the
        // `java.home` system property.

        // In the end, this class should provide the following: Given a fully-qualified class name,
        // return a org.fife.rsta.ac.java.classreader.ClassFile. So, at some point during building the cache, we need to
        // instantiate a `ClassFile` with the file contents of the corresponding `.class` file.
        // Clients will request this information via `createClassFile`. So, probably there the cache should be queried.

        // Please don't hesitate to ask if anything is unclear.

    }

    @Override
    public int compareTo(final LibraryInfo o) {
        // This is only really needed for the definition of `equals` this implies.
        // This is used in `JarManager#addClassFileSource` to check whether a library is already loaded.
        // To compare, we can compare the cache data structures first by number of entries, then (if equal) by contents.
    }

    @Override
    public ClassFile createClassFile(final String entryName) throws IOException {
        // Given a fully qualified class name, query the cache and return the appropriate ClassFile.
        // I think it should not be necessary to handle cache misses since everything should be there already?
    }

    @Override
    public PackageMapNode createPackageMap() throws IOException {
        // It should be sufficient to create a single `PackageMapNode` to which all class names are added.
    }

    @Override
    public long getLastModified() {
        // This can have a trivial implementation since we can assume that there will be no changes and no hotswapping.
    }

    @Override
    public String getLocationAsString() {
        // This can have a trivial implementation since this is never really used.
    }

    @Override
    public int hashCode() {
        // We can use the hashCode of a cache data structure
    }

    @Override
    public void bulkClassFileCreationEnd() throws IOException {
        // Only needed if we do something more sophisticated for `createClassFileBulk`.
    }

    @Override
    public void bulkClassFileCreationStart() throws IOException {
        // Only needed if we do something more sophisticated for `createClassFileBulk`.
    }

    @Override
    public ClassFile createClassFileBulk(final String string) throws IOException {
        // See JavaDoc. We can call the logic of `createClassFile` for simplicity or come up with something better.
    }
}