package com.huaxiaosheng.spapacks.animation.cem;

final class MolangFunctions {
    private MolangFunctions() {
    }

    static MolangExpr compile(String name, MolangExpr[] a, String source) {
        return switch (name) {
            case "sin" -> one(a, name, source, xx -> (float)Math.sin(xx));
            case "cos" -> one(a, name, source, xx -> (float)Math.cos(xx));
            case "tan" -> one(a, name, source, xx -> (float)Math.tan(xx));
            case "asin" -> one(a, name, source, xx -> (float)Math.asin(xx));
            case "acos" -> one(a, name, source, xx -> (float)Math.acos(xx));
            case "atan" -> one(a, name, source, xx -> (float)Math.atan(xx));
            case "sind" -> one(a, name, source, xx -> (float)Math.sin(Math.toRadians(xx)));
            case "cosd" -> one(a, name, source, xx -> (float)Math.cos(Math.toRadians(xx)));
            case "tand" -> one(a, name, source, xx -> (float)Math.tan(Math.toRadians(xx)));
            case "torad" -> one(a, name, source, xx -> (float)Math.toRadians(xx));
            case "todeg" -> one(a, name, source, xx -> (float)Math.toDegrees(xx));
            case "wraprad" -> one(a, name, source, MolangFunctions::wrapRad);
            case "wrapdeg" -> one(a, name, source, MolangFunctions::wrapDeg);
            case "sqrt" -> one(a, name, source, xx -> (float)Math.sqrt(xx));
            case "exp" -> one(a, name, source, xx -> (float)Math.exp(xx));
            case "log" -> one(a, name, source, xx -> (float)Math.log(xx));
            case "log10" -> one(a, name, source, xx -> (float)Math.log10(xx));
            case "abs" -> one(a, name, source, Math::abs);
            case "round" -> one(a, name, source, xx -> Math.round(xx));
            case "floor" -> one(a, name, source, xx -> (float)Math.floor(xx));
            case "ceil" -> one(a, name, source, xx -> (float)Math.ceil(xx));
            case "trunc" -> one(a, name, source, xx -> (float)((long)xx));
            case "frac" -> one(a, name, source, xx -> xx - (float)Math.floor(xx));
            case "sign", "signum" -> one(a, name, source, Math::signum);
            case "hermite_blend" -> one(a, name, source, xx -> 3.0F * xx * xx - 2.0F * xx * xx * xx);
            case "atan2" -> two(a, name, source, (xx, y) -> (float)Math.atan2(xx, y));
            case "min" -> min(a, name, source);
            case "max" -> max(a, name, source);
            case "pow" -> two(a, name, source, (xx, y) -> (float)Math.pow(xx, y));
            case "fmod" -> two(a, name, source, (xx, y) -> y == 0.0F ? 0.0F : xx % y);
            case "clamp" -> {
                arity(a, 3, name, source);
                MolangExpr x = a[0];
                MolangExpr lo = a[1];
                MolangExpr hi = a[2];
                yield v -> {
                    float value = x.eval(v);
                    float low = lo.eval(v);
                    float high = hi.eval(v);
                    return value < low ? low : (value > high ? high : value);
                };
            }
            case "lerp" -> {
                arity(a, 3, name, source);
                MolangExpr x = a[0];
                MolangExpr y = a[1];
                MolangExpr t = a[2];
                yield v -> {
                    float from = x.eval(v);
                    return from + (y.eval(v) - from) * t.eval(v);
                };
            }
            case "between" -> {
                arity(a, 3, name, source);
                MolangExpr x = a[0];
                MolangExpr lo = a[1];
                MolangExpr hi = a[2];
                yield v -> {
                    float value = x.eval(v);
                    return value >= lo.eval(v) && value <= hi.eval(v) ? 1.0F : 0.0F;
                };
            }
            case "in" -> {
                if (a.length < 2) {
                    throw new IllegalArgumentException("in() needs a value and candidates in: " + source);
                }

                yield v -> {
                    float value = a[0].eval(v);

                    for (int i = 1; i < a.length; i++) {
                        if (a[i].eval(v) == value) {
                            return 1.0F;
                        }
                    }

                    return 0.0F;
                };
            }
            case "random" -> random(a);
            default -> throw new IllegalArgumentException("Unknown function '" + name + "' in: " + source);
        };
    }

    private static MolangExpr one(MolangExpr[] a, String name, String source, MolangFunctions.Unary f) {
        arity(a, 1, name, source);
        MolangExpr x = a[0];
        return v -> f.apply(x.eval(v));
    }

    private static MolangExpr two(MolangExpr[] a, String name, String source, MolangFunctions.Binary f) {
        arity(a, 2, name, source);
        MolangExpr x = a[0];
        MolangExpr y = a[1];
        return v -> f.apply(x.eval(v), y.eval(v));
    }

    private static MolangExpr min(MolangExpr[] a, String name, String source) {
        if (a.length < 1) {
            throw new IllegalArgumentException(name + "() needs arguments in: " + source);
        } else {
            return v -> {
                float m = a[0].eval(v);

                for (int i = 1; i < a.length; i++) {
                    m = Math.min(m, a[i].eval(v));
                }

                return m;
            };
        }
    }

    private static MolangExpr max(MolangExpr[] a, String name, String source) {
        if (a.length < 1) {
            throw new IllegalArgumentException(name + "() needs arguments in: " + source);
        } else {
            return v -> {
                float m = a[0].eval(v);

                for (int i = 1; i < a.length; i++) {
                    m = Math.max(m, a[i].eval(v));
                }

                return m;
            };
        }
    }

    private static MolangExpr random(MolangExpr[] a) {
        if (a.length == 0) {
            return v -> (float)Math.random();
        } else if (a.length == 1) {
            MolangExpr seed = a[0];
            return v -> {
                int h = Float.floatToIntBits(seed.eval(v));
                h ^= h >>> 16;
                h *= 73244475;
                h ^= h >>> 16;
                return (h >>> 8) / 1.6777216E7F;
            };
        } else {
            MolangExpr lo = a[0];
            MolangExpr hi = a[1];
            return v -> {
                float low = lo.eval(v);
                return (float)(low + Math.random() * (hi.eval(v) - low));
            };
        }
    }

    private static void arity(MolangExpr[] a, int n, String name, String source) {
        if (a.length != n) {
            throw new IllegalArgumentException(name + "() takes " + n + " argument(s), got " + a.length + " in: " + source);
        }
    }

    private static float wrapRad(float r) {
        float tau = (float) (Math.PI * 2);
        r %= tau;
        if (r > Math.PI) {
            r -= tau;
        }

        if (r < -Math.PI) {
            r += tau;
        }

        return r;
    }

    private static float wrapDeg(float d) {
        d %= 360.0F;
        if (d > 180.0F) {
            d -= 360.0F;
        }

        if (d < -180.0F) {
            d += 360.0F;
        }

        return d;
    }

    @FunctionalInterface
    private interface Binary {
        float apply(float var1, float var2);
    }

    @FunctionalInterface
    private interface Unary {
        float apply(float var1);
    }
}
