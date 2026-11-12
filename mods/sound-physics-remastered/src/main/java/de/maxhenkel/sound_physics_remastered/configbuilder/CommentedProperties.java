package de.maxhenkel.sound_physics_remastered.configbuilder;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class CommentedProperties implements Map<String, String> {
    private final boolean strict;
    private final List<String> headerComments;
    private final Map<String, CommentedProperties.Property> properties;

    public CommentedProperties(boolean strict) {
        this.strict = strict;
        this.headerComments = new ArrayList<>();
        this.properties = new LinkedHashMap<>();
    }

    public CommentedProperties() {
        this(true);
    }

    public CommentedProperties addHeaderComment(String comment) {
        this.headerComments.add(comment);
        return this;
    }

    public CommentedProperties setHeaderComments(List<String> headerComments) {
        this.headerComments.clear();
        this.headerComments.addAll(headerComments);
        return this;
    }

    void sort(Comparator<String> comparator) {
        List<Entry<String, CommentedProperties.Property>> list = new ArrayList<>(this.properties.entrySet());
        list.sort((o1, o2) -> comparator.compare(o1.getKey(), o2.getKey()));
        this.properties.clear();

        for (Entry<String, CommentedProperties.Property> entry : list) {
            this.properties.put(entry.getKey(), entry.getValue());
        }
    }

    @Nullable
    public String get(String key) {
        Objects.requireNonNull(key);
        CommentedProperties.Property property = this.properties.get(key);
        return property == null ? null : property.value;
    }

    @Nullable
    public List<String> getComments(String key) {
        Objects.requireNonNull(key);
        CommentedProperties.Property property = this.properties.get(key);
        return property == null ? null : property.comments;
    }

    public CommentedProperties setComments(String key, List<String> comments) {
        Objects.requireNonNull(key);
        CommentedProperties.Property property = this.properties.get(key);
        if (property == null) {
            this.properties.put(key, new CommentedProperties.Property(comments, ""));
            return this;
        } else {
            property.comments = comments;
            return this;
        }
    }

    public CommentedProperties set(String key, String value, String... comments) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.properties.put(key, new CommentedProperties.Property(Arrays.asList(comments), value));
        return this;
    }

    public CommentedProperties load(InputStream inputStream) throws IOException {
        List<String> headerComments = new ArrayList<>();
        Map<String, CommentedProperties.Property> properties = new LinkedHashMap<>();
        CommentedProperties.LineReader reader = CommentedProperties.LineReader.fromInputStream(inputStream);

        try {
            boolean header = true;
            List<String> previousComments = new ArrayList<>();

            String line;
            while ((line = reader.nextLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (header) {
                        headerComments.addAll(previousComments);
                        previousComments.clear();
                        header = false;
                    }
                } else {
                    CommentedProperties.Pair pair = readLine(line);
                    if (pair.key == null) {
                        previousComments.add(pair.value);
                    } else {
                        CommentedProperties.Property property = new CommentedProperties.Property(pair.value);
                        property.comments.addAll(previousComments);
                        previousComments.clear();
                        properties.put(pair.key, property);
                        header = false;
                    }
                }
            }
        } catch (Throwable var11) {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable var10) {
                    var11.addSuppressed(var10);
                }
            }

            throw var11;
        }

        if (reader != null) {
            reader.close();
        }

        this.setHeaderComments(headerComments);
        this.properties.clear();
        this.properties.putAll(properties);
        return this;
    }

    protected static CommentedProperties.Pair readLine(String line) throws IOException {
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        StringReader reader = new StringReader(line);
        boolean isKey = true;
        boolean isComment = false;
        boolean isPrecedingBackslash = false;
        boolean onlyHadWhitespace = true;
        boolean isStartOfValue = false;

        int c;
        while ((c = reader.read()) != -1) {
            boolean isWhitespace = isWhitespace(c);
            if (isComment) {
                if (!onlyHadWhitespace || !isWhitespace) {
                    onlyHadWhitespace = false;
                    value.append((char)c);
                }
            } else if (isPrecedingBackslash) {
                if (isKey) {
                    key.append((char)readEscapedCharacter(c, reader));
                } else {
                    value.append((char)readEscapedCharacter(c, reader));
                }

                isPrecedingBackslash = false;
                isStartOfValue = false;
            } else if (c == 92) {
                isPrecedingBackslash = true;
            } else if ((c == 35 || c == 33) && onlyHadWhitespace) {
                isComment = true;
            } else {
                if (isKey) {
                    if (key.length() <= 0 && isWhitespace(c)) {
                        continue;
                    }

                    if (isSeparator(c)) {
                        isKey = false;
                        isStartOfValue = true;
                        onlyHadWhitespace = false;
                        continue;
                    }

                    if (isWhitespace(c)) {
                        continue;
                    }

                    key.append((char)c);
                } else {
                    if (isStartOfValue && (isWhitespace(c) || isSeparator(c))) {
                        continue;
                    }

                    value.append((char)c);
                    isStartOfValue = false;
                }

                if (onlyHadWhitespace && !isWhitespace) {
                    onlyHadWhitespace = false;
                }
            }
        }

        return new CommentedProperties.Pair(isComment ? null : key.toString(), value.toString());
    }

    private static int readEscapedCharacter(int c, StringReader reader) throws IOException {
        if (c != 117) {
            if (c == 116) {
                return 9;
            } else if (c == 114) {
                return 13;
            } else if (c == 110) {
                return 10;
            } else {
                return c == 102 ? 12 : c;
            }
        } else {
            int u = 0;

            for (int i = 0; i < 4; i++) {
                int uc = reader.read();
                if (uc == -1) {
                    throw new IOException("Invalid unicode escape sequence");
                }

                u <<= 4;
                if (uc >= 48 && uc <= 57) {
                    u += uc - 48;
                } else if (uc >= 97 && uc <= 102) {
                    u += uc - 97 + 10;
                } else {
                    if (uc < 65 || uc > 70) {
                        throw new IOException("Invalid unicode escape sequence");
                    }

                    u += uc - 65 + 10;
                }
            }

            return u;
        }
    }

    private static boolean isWhitespace(int c) {
        return c == 32 || c == 9 || c == 13 || c == 12 || Character.isWhitespace(c);
    }

    private static boolean isSeparator(int c) {
        return c == 61 || c == 58 || c == 32 || c == 9 || c == 12;
    }

    public CommentedProperties save(OutputStream outputStream) {
        PrintWriter writer = new PrintWriter(outputStream);

        try {
            for (String comment : removeNewLines(this.headerComments)) {
                writer.print("# ");
                writer.println(comment);
            }

            if (this.headerComments.size() > 0) {
                writer.println();
            }

            for (Entry<String, CommentedProperties.Property> entry : this.properties.entrySet()) {
                for (String comment : removeNewLines(entry.getValue().comments)) {
                    writer.print("# ");
                    writer.println(comment);
                }

                writer.print(this.escapeKey(entry.getKey()));
                writer.print("=");
                writer.println(this.escapeValue(entry.getValue().value));
            }

            writer.flush();
        } catch (Throwable var8) {
            try {
                writer.close();
            } catch (Throwable var7) {
                var8.addSuppressed(var7);
            }

            throw var8;
        }

        writer.close();
        return this;
    }

    private static List<String> removeNewLines(List<String> comments) {
        List<String> newComments = new ArrayList<>();

        for (String comment : comments) {
            newComments.addAll(Arrays.asList(comment.split("\\r?\\n")));
        }

        return newComments;
    }

    private String escapeKey(String str) {
        str = this.escape(str);
        str = str.replace(" ", "\\ ");
        str = str.replace("=", "\\=");
        return str.replace(":", "\\:");
    }

    private String escapeValue(String str) {
        str = this.escape(str);
        if (this.strict) {
            str = str.replace("=", "\\=");
            str = str.replace(":", "\\:");
        }

        if (str.startsWith(" ")) {
            str = String.format("\\%s", str);
        }

        return str;
    }

    private String escape(String str) {
        str = str.replace("\\", "\\\\");
        str = str.replace("\n", "\\n");
        str = str.replace("\r", "\\n");
        str = str.replace("\t", "\\t");
        str = str.replace("#", "\\#");
        str = str.replace("!", "\\!");

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (UnicodeBlock.of(c) != UnicodeBlock.BASIC_LATIN) {
                str = String.format("%s\\u%04X%s", str.substring(0, i), Integer.valueOf(c), str.substring(i + 1));
            }
        }

        return str;
    }

    @Override
    public int size() {
        return this.properties.size();
    }

    @Override
    public boolean isEmpty() {
        return this.properties.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.properties.containsKey(key);
    }

    @Deprecated
    @Override
    public boolean containsValue(Object value) {
        for (CommentedProperties.Property property : this.properties.values()) {
            if (property.value.equals(value)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Deprecated
    public String get(Object key) {
        return !(key instanceof String) ? null : this.get((String)key);
    }

    @Deprecated
    public String put(String key, String value) {
        CommentedProperties.Property put = this.properties.put(key, new CommentedProperties.Property(value));
        return put == null ? null : put.value;
    }

    public String remove(Object key) {
        CommentedProperties.Property removed = this.properties.remove(key);
        return removed == null ? null : removed.value;
    }

    @Deprecated
    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
        for (Entry<? extends String, ? extends String> entry : map.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        this.headerComments.clear();
        this.properties.clear();
    }

    @Override
    public Set<String> keySet() {
        return this.properties.keySet();
    }

    @Deprecated
    @Override
    public Collection<String> values() {
        return this.properties.values().stream().map(property -> property.value).collect(Collectors.toList());
    }

    @Deprecated
    @Override
    public Set<Entry<String, String>> entrySet() {
        return this.properties.entrySet().stream().map(entry -> new SimpleEntry<>(entry.getKey(), entry.getValue().value)).collect(Collectors.toSet());
    }

    protected static class LineReader implements Closeable {
        private final BufferedReader reader;

        public LineReader(BufferedReader reader) {
            this.reader = reader;
        }

        public static CommentedProperties.LineReader fromInputStream(InputStream inputStream) {
            return new CommentedProperties.LineReader(new BufferedReader(new InputStreamReader(inputStream)));
        }

        @Nullable
        public String nextLine() throws IOException {
            String line = this.reader.readLine();
            if (line == null) {
                return null;
            } else if (line.endsWith("\\")) {
                line = line.substring(0, line.length() - 1);
                String nextLine = this.nextLine();
                return nextLine == null ? line : line + nextLine;
            } else {
                return line;
            }
        }

        @Override
        public void close() throws IOException {
            this.reader.close();
        }
    }

    protected static class Pair {
        private String key;
        private String value;

        public Pair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    protected static class Property {
        private List<String> comments;
        private String value;

        public Property(List<String> comments, String value) {
            this.comments = comments;
            this.value = value;
        }

        public Property(String value) {
            this(new ArrayList<>(), value);
        }
    }
}
