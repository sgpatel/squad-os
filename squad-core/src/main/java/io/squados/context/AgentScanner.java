package io.squados.context;

import io.squados.annotation.Agent;
import io.squados.config.SquadConfig;
import io.squados.exception.NoAgentFoundException;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * Scans the classpath for classes annotated with {@literal @}Agent.
 *
 * Uses pure java.lang.reflect — no external libraries in Phase 1.
 * Walks the classpath entries, loads each .class file, and checks
 * for the @Agent annotation at runtime.
 *
 * Phase 2 can upgrade this to use Reflections library for speed,
 * but correctness is proven here first.
 */
public class AgentScanner {

    /**
     * Scan for @Agent classes in all packages defined in squad.yml,
     * falling back to the full classpath if no packages are specified.
     *
     * @param config  The parsed squad.yml configuration.
     * @return        List of classes annotated with @Agent.
     * @throws NoAgentFoundException if no @Agent classes are found.
     */
    public static List<Class<?>> scan(SquadConfig config) {
        // Determine which classes to check against @Agent
        // For Phase 1: load classes explicitly listed in squad.yml agents block
        List<Class<?>> found = new ArrayList<>();
        List<String> scanErrors = new ArrayList<>();

        for (SquadConfig.AgentConfig agentCfg : config.getAgents()) {
            String className = agentCfg.getClassName();
            if (className == null || className.isBlank()) continue;
            try {
                Class<?> cls = Class.forName(className);
                if (cls.isAnnotationPresent(Agent.class)) {
                    found.add(cls);
                } else {
                    scanErrors.add("Class '" + className
                        + "' is listed in squad.yml but is not annotated with @Agent. "
                        + "Add @Agent(role=...) to the class declaration.");
                }
            } catch (ClassNotFoundException e) {
                scanErrors.add("Class '" + className
                    + "' listed in squad.yml was not found on the classpath. "
                    + "Check the fully qualified class name and ensure it is compiled.");
            }
        }

        // Report all errors at once rather than stopping at the first one
        if (!scanErrors.isEmpty()) {
            throw new NoAgentFoundException(
                String.join("\n  → ", scanErrors)
            );
        }

        if (found.isEmpty()) {
            throw new NoAgentFoundException(
                "No agents listed under 'agents:' in squad.yml. "
                + "Add at least one agent entry with 'class: com.example.YourAgent'."
            );
        }

        return Collections.unmodifiableList(found);
    }

    /**
     * Package-scan variant — scans all .class files in a given package path.
     * Used when squad.yml does not list explicit agent classes.
     * Returns all classes in the package annotated with @Agent.
     */
    public static List<Class<?>> scanPackage(String packageName) {
        List<Class<?>> found = new ArrayList<>();
        String path = packageName.replace('.', '/');

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File dir = new File(resource.getFile());
                if (dir.exists()) {
                    found.addAll(scanDirectory(dir, packageName));
                }
            }
        } catch (Exception e) {
            // Scan failure is non-fatal; caller checks found.isEmpty()
        }

        return found;
    }

    // ── Internals ─────────────────────────────────────────────────────

    private static List<Class<?>> scanDirectory(File dir, String packageName) {
        List<Class<?>> found = new ArrayList<>();
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                found.addAll(scanDirectory(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "."
                        + file.getName().replace(".class", "");
                try {
                    Class<?> cls = Class.forName(className);
                    if (cls.isAnnotationPresent(Agent.class)) {
                        found.add(cls);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // Skip unloadable classes silently
                }
            }
        }
        return found;
    }
}
