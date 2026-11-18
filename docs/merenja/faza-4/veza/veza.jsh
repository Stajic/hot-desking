import java.sql.*;
String url = "jdbc:postgresql://localhost:54320/hotdesk_5";
String sql = "SELECT r.id, r.name FROM resources r WHERE r.is_active = true ORDER BY r.name";
long[] ot = new long[30]; long[] up = new long[30];
for (int i = 0; i < 5; i++) { try (Connection c = DriverManager.getConnection(url, "postgres", "")) { c.createStatement().executeQuery(sql).close(); } }
for (int i = 0; i < 30; i++) {
  long t0 = System.nanoTime();
  try (Connection c = DriverManager.getConnection(url, "postgres", "")) {
    long t1 = System.nanoTime();
    try (ResultSet rs = c.createStatement().executeQuery(sql)) { while (rs.next()) {} }
    long t2 = System.nanoTime();
    ot[i] = t1 - t0; up[i] = t2 - t1;
  }
}
Connection stalna = DriverManager.getConnection(url, "postgres", "");
long[] us = new long[30];
for (int i = 0; i < 30; i++) { long t0 = System.nanoTime(); try (ResultSet rs = stalna.createStatement().executeQuery(sql)) { while (rs.next()) {} } us[i] = System.nanoTime() - t0; }
stalna.close();
java.util.Arrays.sort(ot); java.util.Arrays.sort(up); java.util.Arrays.sort(us);
System.out.printf("otvaranje nove veze: medijana %.2f ms (min %.2f, max %.2f)%n", ot[15]/1e6, ot[0]/1e6, ot[29]/1e6);
System.out.printf("upit na novoj vezi: medijana %.2f ms%n", up[15]/1e6);
System.out.printf("isti upit na otvorenoj vezi: medijana %.2f ms%n", us[15]/1e6);
/exit
