var alg = com.auth0.jwt.algorithms.Algorithm.HMAC256("razvojna-tajna-ne-koristiti-u-produkciji");
var ver = com.auth0.jwt.JWT.require(alg).withIssuer("hot-desking").withAudience("hot-desking-klijenti").build();
java.util.function.Supplier<String> mk = () -> com.auth0.jwt.JWT.create().withIssuer("hot-desking").withAudience("hot-desking-klijenti").withSubject("6dacfff1-ab8e-4227-95c1-9cd264e9c09b").withClaim("role","USER").withExpiresAt(new java.util.Date(System.currentTimeMillis()+3600000L)).sign(alg);
var tok = mk.get();
long verifyNs(int n) { long t0 = System.nanoTime(); for (int i = 0; i < n; i++) ver.verify(tok); return (System.nanoTime() - t0) / n; }
long signNs(int n)   { long t0 = System.nanoTime(); for (int i = 0; i < n; i++) mk.get(); return (System.nanoTime() - t0) / n; }
verifyNs(300000); signNs(100000);
var v = new long[5]; var s = new long[5];
for (int r = 0; r < 5; r++) { v[r] = verifyNs(300000); s[r] = signNs(100000); }
java.util.Arrays.sort(v); java.util.Arrays.sort(s);
System.out.println("VERIFY_NS=" + java.util.Arrays.toString(v) + " MED=" + v[2]);
System.out.println("SIGN_NS=" + java.util.Arrays.toString(s) + " MED=" + s[2]);
/exit
