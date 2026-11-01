/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.loader;

import java.io.File;
import java.io.IOException;

class ClassLoaderHelper {

    private ClassLoaderHelper() {}

    /**
     * Returns true if loading a native library only if
     * it's present on the file system.
     */
    static boolean loadLibraryOnlyIfPresent() {
        return true;
    }

    /**
     * Returns an alternate path name for the given file
     * such that if the original pathname did not exist, then the
     * file may be located at the alternate location.
     * For most platforms, this behavior is not supported and returns null.
     */
    static File mapAlternativeName(File lib) {
        return null;
    }

    /**
     * Returns the {@link NativeLoadLibrarySpec} for loading the specified native library.
     *
     * @param file a native library
     */
    static NativeLoadLibrarySpec nativeLoadLibrarySpecOf(File file) throws IOException {
        // LoadLibrary appends ".DLL" if the file name has no extension, unless it ends in ".".
        // See: https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-loadlibrarya
        //
        // The canonical path may have symbolic links resolved and is therefore unsuitable for
        // passing to LoadLibrary. By loading the library from the absolute version of the path
        // specified by the caller, and relying on LoadLibrary to resolve any symbolic links itself,
        // we ensure that LoadLibrary will decide whether to append ".DLL" based on the file as
        // given, rather than the potential target if it's a symbolic link.
        //
        // Trailing dots are removed, because Windows and the rest of the JDK treat "foo." as the
        // same file as "foo"; they should not instead tell LoadLibrary not to append ".DLL".
        String path = stripTrailingDots(file.getAbsolutePath());

        // Like on other platforms, the canonical path is used to uniquely identify the library
        // for JNI_OnLoad purposes. It is the canonical path of the file that LoadLibrary will
        // load, so that e.g. "foo", "foo.dll" and symbolic links to it identify the same library.
        String fileName = path.substring(path.lastIndexOf('\\') + 1);
        String loadedPath = (fileName.indexOf('.') < 0) ? path + ".DLL" : path;
        String name = new File(loadedPath).getCanonicalPath();
        return new NativeLoadLibrarySpec(name, path);
    }

    /**
     * Removes trailing dots from the last name in the given path, unless that
     * name consists only of dots.
     */
    private static String stripTrailingDots(String path) {
        int start = path.lastIndexOf('\\') + 1;
        int end = path.length();
        while (end > start && path.charAt(end - 1) == '.') {
            end--;
        }
        return (end > start) ? path.substring(0, end) : path;
    }

    /**
     * Parse a PATH env variable. Windows allows quoted elements in a PATH,
     * so special care needs to be taken.
     *
     * Empty elements will be replaced by dot.
     */
    static String[] parsePath(String ldPath) {
        int ldLen = ldPath.length();
        char ps = File.pathSeparatorChar;
        int psCount = 0;

        if (ldPath.indexOf('\"') >= 0) {
            // First, remove quotes put around quoted parts of paths.
            // Second, use a quotation mark as a new path separator.
            // This will preserve any quoted old path separators.
            char[] buf = new char[ldLen];
            int bufLen = 0;
            for (int i = 0; i < ldLen; ++i) {
                char ch = ldPath.charAt(i);
                if (ch == '\"') {
                    while (++i < ldLen &&
                            (ch = ldPath.charAt(i)) != '\"') {
                        buf[bufLen++] = ch;
                    }
                } else {
                    if (ch == ps) {
                        psCount++;
                        ch = '\"';
                    }
                    buf[bufLen++] = ch;
                }
            }
            ldPath = new String(buf, 0, bufLen);
            ldLen = bufLen;
            ps = '\"';
        } else {
            for (int i = ldPath.indexOf(ps); i >= 0;
                 i = ldPath.indexOf(ps, i + 1)) {
                psCount++;
            }
        }

        String[] paths = new String[psCount + 1];
        int pathStart = 0;
        for (int j = 0; j < psCount; ++j) {
            int pathEnd = ldPath.indexOf(ps, pathStart);
            paths[j] = (pathStart < pathEnd) ?
                    ldPath.substring(pathStart, pathEnd) : ".";
            pathStart = pathEnd + 1;
        }
        paths[psCount] = (pathStart < ldLen) ?
                ldPath.substring(pathStart, ldLen) : ".";
        return paths;
    }
}
