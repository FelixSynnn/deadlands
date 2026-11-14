package com.huaxiaosheng.spapacks.animation.cem;

import java.util.ArrayList;
import java.util.List;

public final class MolangParser {
    private static final int MAX_DEPTH = 200;
    private final String s;
    private final Symbols symbols;
    private int pos;
    private int depth;

    private MolangParser(String source, Symbols symbols) {
        this.s = source;
        this.symbols = symbols;
    }

    public static MolangExpr parse(String source, Symbols symbols) {
        if (source != null && !source.isBlank()) {
            MolangParser p = new MolangParser(source, symbols);
            MolangExpr e = p.or();
            p.skipWs();
            if (p.pos < p.s.length()) {
                throw new IllegalArgumentException("Unexpected '" + p.s.charAt(p.pos) + "' at " + p.pos + " in: " + source);
            } else {
                return e;
            }
        } else {
            return v -> 0.0F;
        }
    }

    private MolangExpr or() {
        this.descend();
        MolangExpr left = this.and();

        while (this.match("||")) {
            MolangExpr l = left;
            MolangExpr r = this.and();
            left = v -> l.eval(v) == 0.0F && r.eval(v) == 0.0F ? 0.0F : 1.0F;
        }

        this.depth--;
        return left;
    }

    private MolangExpr and() {
        MolangExpr left = this.equality();

        while (this.match("&&")) {
            MolangExpr l = left;
            MolangExpr r = this.equality();
            left = v -> l.eval(v) != 0.0F && r.eval(v) != 0.0F ? 1.0F : 0.0F;
        }

        return left;
    }

    private MolangExpr equality() {
        MolangExpr left = this.comparison();

        while (true) {
            MolangExpr l = left;
            if (this.match("==")) {
                MolangExpr r = this.comparison();
                left = v -> l.eval(v) == r.eval(v) ? 1.0F : 0.0F;
            } else {
                if (!this.match("!=")) {
                    return left;
                }

                MolangExpr r = this.comparison();
                left = v -> l.eval(v) != r.eval(v) ? 1.0F : 0.0F;
            }
        }
    }

    private MolangExpr comparison() {
        MolangExpr left = this.additive();

        while (true) {
            MolangExpr l = left;
            if (this.match(">=")) {
                MolangExpr r = this.additive();
                left = v -> l.eval(v) >= r.eval(v) ? 1.0F : 0.0F;
            } else if (this.match("<=")) {
                MolangExpr r = this.additive();
                left = v -> l.eval(v) <= r.eval(v) ? 1.0F : 0.0F;
            } else if (this.match(">")) {
                MolangExpr r = this.additive();
                left = v -> l.eval(v) > r.eval(v) ? 1.0F : 0.0F;
            } else {
                if (!this.match("<")) {
                    return left;
                }

                MolangExpr r = this.additive();
                left = v -> l.eval(v) < r.eval(v) ? 1.0F : 0.0F;
            }
        }
    }

    private MolangExpr additive() {
        MolangExpr left = this.term();

        while (true) {
            this.skipWs();
            char op = this.peek();
            if (op != '+' && op != '-') {
                return left;
            }

            this.pos++;
            MolangExpr l = left;
            MolangExpr r = this.term();
            left = op == '+' ? v -> l.eval(v) + r.eval(v) : v -> l.eval(v) - r.eval(v);
        }
    }

    private MolangExpr term() {
        MolangExpr left = this.unary();

        while (true) {
            this.skipWs();
            char op = this.peek();
            if (op != '*' && op != '/' && op != '%') {
                return left;
            }

            this.pos++;
            MolangExpr l = left;
            MolangExpr r = this.unary();

            left = switch (op) {
                case '*' -> v -> l.eval(v) * r.eval(v);
                case '/' -> v -> {
                    float d = r.eval(v);
                    return d == 0.0F ? 0.0F : l.eval(v) / d;
                };
                default -> v -> {
                    float d = r.eval(v);
                    return d == 0.0F ? 0.0F : l.eval(v) % d;
                };
            };
        }
    }

    private MolangExpr unary() {
        this.descend();
        MolangExpr e = this.unaryOperand();
        this.depth--;
        return e;
    }

    private void descend() {
        if (++this.depth > 200) {
            throw new IllegalArgumentException("Expression nests deeper than 200 in: " + this.s);
        }
    }

    private MolangExpr unaryOperand() {
        this.skipWs();
        char c = this.peek();
        if (c == '+') {
            this.pos++;
            return this.unary();
        } else if (c == '-') {
            this.pos++;
            MolangExpr operand = this.unary();
            return v -> -operand.eval(v);
        } else if (c == '!') {
            this.pos++;
            MolangExpr operand = this.unary();
            return v -> operand.eval(v) == 0.0F ? 1.0F : 0.0F;
        } else {
            return this.primary();
        }
    }

    private MolangExpr primary() {
        this.skipWs();
        char c = this.peek();
        if (c == '(') {
            this.pos++;
            MolangExpr e = this.or();
            this.expect(')');
            return e;
        } else if (isDigit(c) || c == '.') {
            return this.number();
        } else if (isIdentStart(c)) {
            return this.identifier();
        } else {
            throw new IllegalArgumentException("Unexpected '" + c + "' at " + this.pos + " in: " + this.s);
        }
    }

    private MolangExpr identifier() {
        int start = this.pos;

        while (this.pos < this.s.length() && isIdentPart(this.s.charAt(this.pos))) {
            this.pos++;
        }

        String name = this.s.substring(start, this.pos);
        this.skipWs();
        if (this.peek() == '(') {
            return this.call(name);
        }

        return switch (name) {
            case "pi" -> v -> (float) Math.PI;
            case "e" -> v -> (float) Math.E;
            case "true" -> v -> 1.0F;
            case "false" -> v -> 0.0F;
            default -> {
                int slot = this.symbols.slot(name);
                yield v -> v[slot];
            }
        };
    }

    private MolangExpr call(String name) {
        if (name.equals("nbt")) {
            this.expect('(');
            String path = this.rawArgument();
            Float wanted = null;
            int mark = this.pos;
            if (this.pos < this.s.length() && this.s.charAt(this.pos) == ',') {
                this.pos++;
                String raw = this.rawArgument().replace("\"", "").replace("'", "").trim();

                try {
                    wanted = Float.parseFloat(raw);
                } catch (NumberFormatException e) {
                    this.pos = mark;
                }
            }

            this.skipToClose();
            int slot = this.symbols.slot("nbt:" + path);
            if (wanted != null) {
                float w = wanted;
                return v -> v[slot] == w ? 1.0F : 0.0F;
            } else {
                return v -> v[slot];
            }
        } else {
            this.expect('(');
            List<MolangExpr> args = new ArrayList<>();
            this.skipWs();
            if (this.peek() != ')') {
                args.add(this.or());
                this.skipWs();

                while (this.peek() == ',') {
                    this.pos++;
                    args.add(this.or());
                    this.skipWs();
                }
            }

            this.expect(')');
            MolangExpr[] a = args.toArray(new MolangExpr[0]);
            return name.equals("if") ? this.conditional(a) : MolangFunctions.compile(name, a, this.s);
        }
    }

    private MolangExpr conditional(MolangExpr[] a) {
        if (a.length < 3 || a.length % 2 == 0) {
            throw new IllegalArgumentException("if() needs (cond, then, ..., else) in: " + this.s);
        } else if (a.length == 3) {
            MolangExpr test = a[0];
            MolangExpr yes = a[1];
            MolangExpr no = a[2];
            return v -> test.eval(v) != 0.0F ? yes.eval(v) : no.eval(v);
        } else {
            return v -> {
                for (int i = 0; i + 1 < a.length; i += 2) {
                    if (a[i].eval(v) != 0.0F) {
                        return a[i + 1].eval(v);
                    }
                }

                return a[a.length - 1].eval(v);
            };
        }
    }

    private String rawArgument() {
        this.skipWs();
        int start = this.pos;

        while (this.pos < this.s.length() && this.s.charAt(this.pos) != ',' && this.s.charAt(this.pos) != ')') {
            this.pos++;
        }

        return this.s.substring(start, this.pos).trim();
    }

    private void skipToClose() {
        int open = 1;

        while (this.pos < this.s.length() && open > 0) {
            char c = this.s.charAt(this.pos++);
            if (c == '(') {
                open++;
            } else if (c == ')') {
                open--;
            }
        }

        if (open != 0) {
            throw new IllegalArgumentException("Unbalanced nbt(...) in: " + this.s);
        }
    }

    private MolangExpr number() {
        int start = this.pos;

        while (this.pos < this.s.length() && (isDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '.')) {
            this.pos++;
        }

        float value = Float.parseFloat(this.s.substring(start, this.pos));
        return v -> value;
    }

    private boolean match(String token) {
        this.skipWs();
        if (this.s.startsWith(token, this.pos)) {
            this.pos = this.pos + token.length();
            return true;
        } else {
            return false;
        }
    }

    private void skipWs() {
        while (this.pos < this.s.length() && Character.isWhitespace(this.s.charAt(this.pos))) {
            this.pos++;
        }
    }

    private char peek() {
        return this.pos < this.s.length() ? this.s.charAt(this.pos) : '\u0000';
    }

    private void expect(char c) {
        this.skipWs();
        if (this.peek() != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at " + this.pos + " in: " + this.s);
        }

        this.pos++;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c) || c == '.';
    }
}
