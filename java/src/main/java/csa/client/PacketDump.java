package csa.client;

import java.nio.charset.Charset;

/**
 * Hex + pseudo-decode helper for real-client frame calibration.
 * Usage: PacketDump.hex(byte[]) or PacketDump.explain(String line)
 */
public final class PacketDump {

    public static final Charset GBK = Charset.forName("GBK");

    private PacketDump() {}

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 3);
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format("%02X ", b[i]));
            if ((i + 1) % 16 == 0) sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Explain one lssproto line: "id cmd tokens..."
     * Tokens shown raw and base62-decoded when numeric-looking.
     */
    public static String explain(String line) {
        if (line == null || line.isEmpty()) return "<empty>";
        String[] p = line.trim().split(" ");
        StringBuilder sb = new StringBuilder();
        sb.append("raw=[").append(line.trim()).append("]\n");
        if (p.length >= 2) {
            sb.append("  msgId=").append(p[0]).append(" cmd=").append(p[1]).append('\n');
            for (int i = 2; i < p.length; i++) {
                String t = p[i];
                if (t.isEmpty()) continue;
                String dec;
                try {
                    dec = String.valueOf(csa.net.lssp.LssProtocolCodec.demInt(t));
                } catch (Exception e) {
                    dec = "(str)";
                }
                String unesc;
                try {
                    unesc = csa.net.lssp.LssProtocolCodec.demString(t);
                } catch (Exception e) {
                    unesc = t;
                }
                sb.append("  arg[").append(i - 2).append("] raw=")
                  .append(t).append("  asInt=").append(dec)
                  .append("  asStr=").append(unesc.replace("\n", "\\n")).append('\n');
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        // self demo
        System.out.println(explain("1 ClientLogin dXhlcg G8 hElv-abc123 1"));
    }
}