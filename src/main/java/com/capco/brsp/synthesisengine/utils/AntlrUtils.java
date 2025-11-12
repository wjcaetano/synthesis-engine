package com.capco.brsp.synthesisengine.utils;

import org.antlr.v4.Tool;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public final class AntlrUtils {

    private static final String PACKAGE_NAME = "antlr4";

    private AntlrUtils() {
    }

    public static LoadedGrammars load(String lexerGrammarG4, String parserGrammarG4, Map<String, String> dependencies) {
        Objects.requireNonNull(lexerGrammarG4, "lexerGrammarG4");
        Objects.requireNonNull(parserGrammarG4, "parserGrammarG4");

        try {
            Path work = Files.createTempDirectory("antlr-load-");
            Path genDir = work.resolve("gen");
            Path classesDir = work.resolve("classes");
            Files.createDirectories(genDir);
            Files.createDirectories(classesDir);

            String lexerName = extractGrammarName(lexerGrammarG4);
            String parserName = extractGrammarName(parserGrammarG4);

            Path lexerFile = genDir.resolve(lexerName + ".g4");
            Path parserFile = genDir.resolve(parserName + ".g4");

            String parserFixed = ensureTokenVocab(parserGrammarG4, lexerName);

            Files.writeString(lexerFile, lexerGrammarG4, StandardCharsets.UTF_8);
            Files.writeString(parserFile, parserFixed, StandardCharsets.UTF_8);

            for (Map.Entry<String, String> dependencyEntry : dependencies.entrySet()) {
                String fqn = dependencyEntry.getKey();
                String src = dependencyEntry.getValue();
                if (isGrammar(src)) {
                    String name = extractGrammarName(src);
                    Files.writeString(genDir.resolve(name + ".g4"), src, StandardCharsets.UTF_8);
                } else {
                    Path javaPath = genDir.resolve(fqn.replace('.', '/') + ".java");
                    Files.createDirectories(javaPath.getParent());
                    Files.writeString(javaPath, src, StandardCharsets.UTF_8);
                }
            }

            List<String> args = new ArrayList<>();
            args.add("-visitor");
            args.add("-listener");
            args.add("-o");
            args.add(genDir.toString());
            args.add("-package");
            args.add(PACKAGE_NAME);
            args.add(lexerFile.toString());
            args.add(parserFile.toString());

            Tool antlr = new Tool(args.toArray(new String[0]));
            antlr.processGrammarsOnCommandLine();

            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            if (javac == null) throw new IllegalStateException("No system Java compiler found (use a JDK).");

            List<String> javaFiles = new ArrayList<>();
            try (var s = Files.walk(genDir)) {
                s.filter(p -> p.toString().endsWith(".java")).forEach(p -> javaFiles.add(p.toString()));
            }
            if (javaFiles.isEmpty()) throw new IllegalStateException("ANTLR produced no Java sources.");

            String runtimeCp = System.getProperty("java.class.path", "");
            String cp = runtimeCp + File.pathSeparator + genDir + File.pathSeparator + classesDir;

            List<String> jcArgs = new ArrayList<>();
            jcArgs.add("-encoding");
            jcArgs.add("UTF-8");
            jcArgs.add("-d");
            jcArgs.add(classesDir.toString());
            jcArgs.add("-cp");
            jcArgs.add(cp);
            jcArgs.addAll(javaFiles);

            int rc = javac.run(null, null, null, jcArgs.toArray(new String[0]));
            if (rc != 0) throw new IllegalStateException("javac failed with exit code " + rc);

            GeneratedClassLoader cl = new GeneratedClassLoader(new URL[]{classesDir.toUri().toURL()}, AntlrUtils.class.getClassLoader());

            Class<?> lexerClz = cl.loadClass(PACKAGE_NAME + "." + lexerName);
            Class<?> parserClz = cl.loadClass(PACKAGE_NAME + "." + parserName);

            return new LoadedGrammars(lexerClz, parserClz, cl, work, genDir, classesDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load grammars: " + e.getMessage(), e);
        }
    }

    public static final class LoadedGrammars implements AutoCloseable {
        public final Class<?> lexerClass;
        public final Class<?> parserClass;
        public final ClassLoader classLoader;
        public final Path workspace;
        public final Path sourcesDir;
        public final Path classesDir;

        private LoadedGrammars(Class<?> lexerClass, Class<?> parserClass,
                               ClassLoader classLoader,
                               Path workspace, Path sourcesDir, Path classesDir) {
            this.lexerClass = lexerClass;
            this.parserClass = parserClass;
            this.classLoader = classLoader;
            this.workspace = workspace;
            this.sourcesDir = sourcesDir;
            this.classesDir = classesDir;
        }

        @Override
        public void close() throws Exception {
            if (classLoader instanceof URLClassLoader u) u.close();
            deleteRecursive(workspace);
        }
    }

    private static final Pattern GRAMMAR_DECL = Pattern.compile(
            "\\b(grammar)\\s+([A-Za-z_][A-Za-z_0-9]*)\\s*;", Pattern.MULTILINE);

    private static boolean isGrammar(String g4) {
        String s = g4.replaceAll("(?s)/\\*.*?\\*/", "");
        s = s.replaceAll("(?m)//.*?$", "");
        var m = GRAMMAR_DECL.matcher(s);

        return m.find();
    }

    private static String extractGrammarName(String g4) {
        String s = g4.replaceAll("(?s)/\\*.*?\\*/", "");
        s = s.replaceAll("(?m)//.*?$", "");
        var m = GRAMMAR_DECL.matcher(s);
        if (!m.find()) {
            String head = g4.substring(0, Math.min(g4.length(), 140)).replace("\n", "\\n");
            throw new IllegalArgumentException("Cannot find `grammar <Name>;` in: " + head + (g4.length() > 140 ? " …" : ""));
        }
        return m.group(2);
    }

    private static String ensureTokenVocab(String parserG4, String lexerName) {
        if (Pattern.compile("\\btokenVocab\\s*=\\s*" + Pattern.quote(lexerName) + "\\b")
                .matcher(parserG4).find()) {
            return parserG4;
        }

        if (Pattern.compile("\\btokenVocab\\s*=\\s*\\w+\\b").matcher(parserG4).find()) {
            return parserG4;
        }

        Pattern optionsPat = Pattern.compile("(?s)\\boptions\\s*\\{([^}]*)\\}");
        var m = optionsPat.matcher(parserG4);
        if (m.find()) {
            String body = m.group(1);
            if (!Pattern.compile("\\btokenVocab\\s*=").matcher(body).find()) {
                String merged = m.replaceFirst("options {$1\n  tokenVocab=" + lexerName + "; }");
                return merged;
            }
            return parserG4;
        }

        String name = extractGrammarName(parserG4);
        String header = "parser grammar " + name + ";";
        int idx = parserG4.indexOf(header);
        if (idx >= 0) {
            String inject = "\noptions { tokenVocab=" + lexerName + "; }\n";
            return parserG4.substring(0, idx + header.length()) + inject + parserG4.substring(idx + header.length());
        }

        return parserG4;
    }

    public static class GeneratedClassLoader extends URLClassLoader {
        public GeneratedClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith(PACKAGE_NAME + ".")) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            c = findClass(name);
                        } catch (ClassNotFoundException ignore) {
                        }
                    }
                    if (c != null) {
                        if (resolve) resolveClass(c);
                        return c;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }
    }

    @SuppressWarnings("unused")
    private static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.comparingInt(p -> -p.getNameCount()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
