package moon.visual.shader;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resource and include methods of com.moon.h_. No migration fixes are implicit. */
public final class ShaderSourceLoader {
    public interface ResourceAccess {
        InputStream open(String absolutePath) throws IOException;
    }

    private static final Pattern INCLUDE = Pattern.compile("#include <(?<path>.*)>");
    private final ResourceAccess resources;
    private final ShaderDiagnostics diagnostics;

    public ShaderSourceLoader(ResourceAccess resources, ShaderDiagnostics diagnostics) {
        this.resources = resources;
        this.diagnostics = diagnostics;
    }

    /** Supply the original program's class as anchor when preserving classloader lookup. */
    public static ShaderSourceLoader classpath(Class<?> anchor, ShaderDiagnostics diagnostics) {
        return new ShaderSourceLoader(anchor::getResourceAsStream, diagnostics);
    }

    public String readResource(String name) throws IOException {
        InputStream input = resources.open(String.format("/engine/shaders/%s", name));
        if (input == null) {
            diagnostics.error("Could not find input shader file {}", name);
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
        // The original uses the default charset and does not close this resource stream.
        return preprocess(new String(bytes.toByteArray()));
    }

    public String preprocess(String source) {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(source))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line.startsWith("#include") ? resolveInclude(line) : line).append("\n");
            }
        } catch (Exception exception) {
            // h_ returns accumulated text after Exception; Errors still propagate.
            exception.printStackTrace();
        }
        return result.toString();
    }

    private String resolveInclude(String line) throws IOException {
        Matcher matcher = INCLUDE.matcher(line);
        if (!matcher.matches()) {
            diagnostics.error("Invalid import statement: {}", line);
            return line;
        }
        // No cycle/path guard in the original. Missing include appends literal "null".
        return readResource(matcher.group("path"));
    }
}
