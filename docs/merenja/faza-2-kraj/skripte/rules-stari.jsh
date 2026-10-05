import rs.ftn.hotdesk.shared.rules.BookingRules;
var R = BookingRules.INSTANCE;
long SLOT = BookingRules.SLOT_MILLIS;
long OFF = 7_200_000L;
long now = 1_791_200_000_000L;
long day = R.dayStart(now + 86_400_000L, OFF);
long s = day + 2 * SLOT;
long e = s + 2 * SLOT;
long sink = 0;
double valNs(int n) { long t0 = System.nanoTime(); for (int i = 0; i < n; i++) sink += R.validate(s, e, now - (i & 1023), OFF).hashCode(); return (System.nanoTime() - t0) / (double) n; }
double dayNs(int n) { long t0 = System.nanoTime(); for (int i = 0; i < n; i++) sink += R.dayStart(now + i * 1000L, OFF); return (System.nanoTime() - t0) / (double) n; }
valNs(1_000_000); dayNs(1_000_000);
var v = new double[5]; var d = new double[5];
for (int r = 0; r < 5; r++) { v[r] = valNs(2_000_000); d[r] = dayNs(2_000_000); }
java.util.Arrays.sort(v); java.util.Arrays.sort(d);
System.out.println("VALIDATE_NS=" + java.util.Arrays.toString(v) + " MED=" + v[2]);
System.out.println("DAYSTART_NS=" + java.util.Arrays.toString(d) + " MED=" + d[2]);
System.out.println("SINK=" + (sink != 42));
/exit
