/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 *
 * @author pron
 */
public class PathClassLoader extends ClassLoader {
    private final Object[] paths;

    public PathClassLoader(Path[] paths, ClassLoader parent) throws IOException {
        super(parent);
        this.paths = process(paths);
    }

    public PathClassLoader(Path[] paths) throws IOException {
        this.paths = process(paths);
    }

    private static Object[] process(Path[] paths) throws IOException {
        try {
            final Object[] os = new Object[paths.length];
            for (int i = 0; i < paths.length; i++) {
                final Path p = paths[i];
                final Object o;
                if (Files.isRegularFile(p))
                    o = FileSystems.newFileSystem(p, null);
                else
                    o = p;
                os[i] = o;
            }
            return os;
        } catch (ProviderNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public final void close() {
        IOException ex = null;
        for (Object p : paths) {
            if (p instanceof FileSystem) {
                try {
                    ((FileSystem) p).close();
                } catch (IOException e) {
                    if (ex == null)
                        ex = e;
                    else
                        ex.addSuppressed(e);
                }
            }
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream is = super.getResourceAsStream(name);
        if (is == null)
            is = findResourceAsStream(name);
        return is;
    }

    private Path resolve(Object o, String name) {
        final Path p;
        if (o instanceof FileSystem)
            p = ((FileSystem) o).getPath(name);
        else
            p = ((Path) o).resolve(name);
        return p;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        final byte[] buf = readResource(name.replace('.', '/') + ".class");
        if (buf == null)
            throw new ClassNotFoundException(name);
        return defineClass(name, buf, 0, buf.length);
    }

    @Override
    protected URL findResource(String name) {
        try {
            for (Object o : paths) {
                final Path p = resolve(o, name);
                if (Files.exists(p))
                    return p.toUri().toURL();
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        try {
            List<URL> urls = new ArrayList<>();
            for (Object o : paths) {
                final Path p = resolve(o, name);
                if (Files.exists(p))
                    urls.add(p.toUri().toURL());
            }
            return Collections.enumeration(urls);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream findResourceAsStream(String name) {
        try {
            for (Object o : paths) {
                final Path p = resolve(o, name);
                if (isFileResource(p))
                    return Files.newInputStream(p);
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readResource(String name) {
        try {
            for (Object o : paths) {
                final Path p = resolve(o, name);
                if (isFileResource(p))
                    return Files.readAllBytes(p);
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isFileResource(Path p) {
        return Files.exists(p) && !Files.isDirectory(p);
    }
}
