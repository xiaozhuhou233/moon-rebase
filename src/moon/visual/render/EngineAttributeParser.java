package moon.visual.render;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Migration parser for the 14 recovered FLOAT-only files; NOT the original reflection parser. */
public final class EngineAttributeParser implements ShaderAttributeSet.Parser {
    private static final Pattern FILE=Pattern.compile("\\s*path\\(\"[^\"]*\"\\)\\s*data\\(\\[(.*)\\]\\)\\s*",Pattern.DOTALL);
    private static final Pattern ENTRY=Pattern.compile("\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\{\\s*size\\s*=\\s*([1-4])\\s*,\\s*dataType\\s*=\\s*FLOAT\\s*\\}\\s*,?\\s*");
    public List<ShaderAttribute> parse(InputStream input) throws IOException {
        StringBuilder text=new StringBuilder();
        try(Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            char[] b=new char[4096];int n;while((n=reader.read(b))!=-1)text.append(b,0,n);
        }
        Matcher file=FILE.matcher(text);
        if(!file.matches())throw new IOException("Unsupported attribute grammar");
        String body=file.group(1);int pos=0;List<ShaderAttribute> result=new ArrayList<>();
        while(pos<body.length()&&!body.substring(pos).trim().isEmpty()) {
            Matcher m=ENTRY.matcher(body);m.region(pos,body.length());
            if(!m.lookingAt())throw new IOException("Unsupported attribute at "+pos);
            result.add(new ShaderAttribute(Integer.parseInt(m.group(2)),5126,4,"FLOAT",m.group(1)));
            pos=m.end();
        }
        return result;
    }
}
